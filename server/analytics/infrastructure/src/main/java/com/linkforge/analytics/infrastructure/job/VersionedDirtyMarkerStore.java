package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;

/** Redis V2 dirty marker 的公平 generation claim 与 compare-and-delete 边界。 */
final class VersionedDirtyMarkerStore {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local limit = tonumber(ARGV[1])
            if not limit or limit < 1 then
                limit = 1
            end

            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[2], KEYS[3])
                return {}
            end

            local claims = {}
            local selected = {}
            local inspected = 0
            while (#claims / 2) < limit and inspected < limit do
                local member = redis.call('LPOP', KEYS[3])
                if not member then
                    break
                end
                inspected = inspected + 1
                if not selected[member] then
                    local generation = redis.call('HGET', KEYS[1], member)
                    local numericGeneration = generation and tonumber(generation)
                    if numericGeneration and numericGeneration > 0 then
                        table.insert(claims, member)
                        table.insert(claims, generation)
                        selected[member] = true
                    end
                end
            end

            if (#claims / 2) < limit and redis.call('LLEN', KEYS[3]) == 0 then
                local cursor = redis.call('GET', KEYS[2]) or '0'
                if not string.match(cursor, '^%d+$') then
                    cursor = '0'
                end
                local scan = redis.call('HSCAN', KEYS[1], cursor, 'COUNT', limit)
                local nextCursor = scan[1]
                local entries = scan[2]
                for i = 1, #entries, 2 do
                    local member = entries[i]
                    local generation = entries[i + 1]
                    local numericGeneration = generation and tonumber(generation)
                    if numericGeneration and numericGeneration > 0 and not selected[member] then
                        if (#claims / 2) < limit then
                            table.insert(claims, member)
                            table.insert(claims, generation)
                            selected[member] = true
                        else
                            redis.call('RPUSH', KEYS[3], member)
                        end
                    end
                end
                if nextCursor == '0' then
                    redis.call('DEL', KEYS[2])
                else
                    redis.call('SET', KEYS[2], nextCursor)
                end
            end

            local markerTtl = redis.call('PTTL', KEYS[1])
            if markerTtl > 0 then
                if redis.call('EXISTS', KEYS[2]) == 1 then
                    redis.call('PEXPIRE', KEYS[2], markerTtl)
                end
                if redis.call('EXISTS', KEYS[3]) == 1 then
                    redis.call('PEXPIRE', KEYS[3], markerTtl)
                end
            elseif markerTtl == -1 then
                redis.call('PERSIST', KEYS[2])
                redis.call('PERSIST', KEYS[3])
            else
                redis.call('DEL', KEYS[2], KEYS[3])
            end
            return claims
            """, List.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            local completed = 0
            local conflicts = 0
            for i = 1, #ARGV, 2 do
                local member = ARGV[i]
                local claimedGeneration = ARGV[i + 1]
                local currentGeneration = redis.call('HGET', KEYS[1], member)
                if currentGeneration == claimedGeneration then
                    redis.call('HDEL', KEYS[1], member)
                    redis.call('HDEL', KEYS[2], member)
                    completed = completed + 1
                elseif currentGeneration then
                    conflicts = conflicts + 1
                end
            end
            if redis.call('HLEN', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[3], KEYS[4])
            else
                local markerTtl = redis.call('PTTL', KEYS[1])
                if markerTtl > 0 then
                    if redis.call('EXISTS', KEYS[3]) == 1 then
                        redis.call('PEXPIRE', KEYS[3], markerTtl)
                    end
                    if redis.call('EXISTS', KEYS[4]) == 1 then
                        redis.call('PEXPIRE', KEYS[4], markerTtl)
                    end
                elseif markerTtl == -1 then
                    redis.call('PERSIST', KEYS[3])
                    redis.call('PERSIST', KEYS[4])
                end
            end
            return {completed, conflicts}
            """, List.class);

    private final StringRedisTemplate redis;

    VersionedDirtyMarkerStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 公平扫描一批 marker 并冻结其 generation。
     *
     * <p>HSCAN cursor 和超出本批上限的紧凑 Hash 扫描结果持久化在 Redis，并跟随 marker TTL；因此多实例与进程
     * 重启会共享轮转进度。每次调用只执行一次带 COUNT 的 HSCAN，返回数量严格不超过 {@code limit}。扫描期间的
     * 新写入可能留到后续完整轮转，但不会因本次完成而误删。</p>
     */
    List<Claim> claim(String markerKey, String firstSeenKey, int limit) {
        int safeLimit = Math.max(limit, 1);
        HashOperations<String, Object, Object> hashes = redis.opsForHash();
        @SuppressWarnings("unchecked")
        List<Object> rawClaims = redis.execute(
                CLAIM_SCRIPT,
                List.of(markerKey, claimCursorKey(markerKey), claimOverflowKey(markerKey)),
                String.valueOf(safeLimit)
        );
        if (rawClaims == null || rawClaims.size() % 2 != 0 || rawClaims.size() > (long) safeLimit * 2L) {
            throw new IllegalStateException("versioned dirty marker claim returned an invalid result");
        }

        List<Claim> claims = new ArrayList<>(rawClaims.size() / 2);
        for (int i = 0; i < rawClaims.size(); i += 2) {
            String member = text(rawClaims.get(i));
            long generation = positiveLong(rawClaims.get(i + 1));
            if (member != null && generation > 0) {
                claims.add(new Claim(member, generation, 0L));
            }
        }
        if (claims.isEmpty()) {
            return List.of();
        }

        List<Object> members = claims.stream().map(Claim::member).map(v -> (Object) v).toList();
        List<Object> firstSeenValues = hashes.multiGet(firstSeenKey, members);
        List<Claim> withFirstSeen = new ArrayList<>(claims.size());
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);
            Object raw = firstSeenValues == null || i >= firstSeenValues.size() ? null : firstSeenValues.get(i);
            withFirstSeen.add(new Claim(claim.member(), claim.generation(), positiveLong(raw)));
        }
        return List.copyOf(withFirstSeen);
    }

    /**
     * 仅当当前 generation 仍等于 claim 时删除 marker；已推进的 generation 计为冲突并保留给下一轮。
     * marker 清空时同时清理轮转 cursor 和 overflow 状态。
     */
    Completion complete(String markerKey, String firstSeenKey, List<Claim> claims) {
        if (claims == null || claims.isEmpty()) {
            return new Completion(0L, 0L);
        }
        List<String> args = new ArrayList<>(claims.size() * 2);
        for (Claim claim : claims) {
            if (claim == null || claim.member() == null || claim.member().isBlank() || claim.generation() <= 0) {
                continue;
            }
            args.add(claim.member());
            args.add(String.valueOf(claim.generation()));
        }
        if (args.isEmpty()) {
            return new Completion(0L, 0L);
        }

        @SuppressWarnings("unchecked")
        List<Object> result = redis.execute(
                COMPLETE_SCRIPT,
                List.of(markerKey, firstSeenKey, claimCursorKey(markerKey), claimOverflowKey(markerKey)),
                args.toArray()
        );
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("versioned dirty marker completion returned no result");
        }
        return new Completion(nonNegativeLong(result.get(0)), nonNegativeLong(result.get(1)));
    }

    private static String claimCursorKey(String markerKey) {
        return AnalyticsKeys.dirtyMarkerClaimCursorKey(markerKey);
    }

    private static String claimOverflowKey(String markerKey) {
        return AnalyticsKeys.dirtyMarkerClaimOverflowKey(markerKey);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static long positiveLong(Object value) {
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static long nonNegativeLong(Object value) {
        try {
            return Math.max(Long.parseLong(String.valueOf(value)), 0L);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    record Claim(String member, long generation, long firstSeenEpochMillis) {
    }

    record Completion(long completed, long generationConflicts) {
    }
}
