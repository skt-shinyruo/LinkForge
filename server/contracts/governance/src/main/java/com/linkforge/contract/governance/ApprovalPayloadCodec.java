package com.linkforge.contract.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Governance 结构化审批快照的严格 JSON codec。
 *
 * <p>codec 使用 Jackson {@code JavaTimeModule}，时间类型输出 ISO-8601 文本而不是数字时间戳；是否按 UTC
 * 解释仍由具体 payload 契约决定。反序列化开启未知字段失败，因此新增字段会使旧消费者拒绝该 JSON，而不是静默
 * 丢弃信息。缺失字段、空值、数值范围以及业务资源范围并不由本类统一校验，消费方必须按操作类型验证。</p>
 *
 * <p>{@link #read(String, Class)} 只知道目标 Java 类型，不会自动比较 payload 内的 {@code type} 和
 * {@code version}，也不能解析历史自由文本快照。每个执行器或审批规则都必须先选择正确的 payload 类，再显式
 * 校验 type/version；不兼容变更应新增版本而不是改变 V1 字段语义。</p>
 *
 * <p>本工具不读写数据库、不加入事务，也不提供重试或 exactly-once 语义。序列化/反序列化失败只转换 Jackson
 * 的 {@link JsonProcessingException} 为 {@link IllegalArgumentException}，其他调用错误仍按其原始运行时异常
 * 向上传播。</p>
 */
public final class ApprovalPayloadCodec {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ApprovalPayloadCodec() {
    }

    /**
     * 将一个 Jackson 可编码对象写成 JSON 文本。
     *
     * <p>审批生产者应传入带稳定 {@code type}/{@code version} 的 payload record。传入 {@code null} 会得到 JSON
     * {@code null}，但这不是当前任何结构化审批操作的有效业务快照；本方法不会替调用方补充类型、版本或必填字段。</p>
     *
     * @param payload 要编码的对象
     * @return JSON 文本；Java 时间类型采用 ISO-8601 文本表示
     * @throws IllegalArgumentException Jackson 无法生成 JSON 时抛出，并保留原始异常作为原因
     */
    public static String write(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("approval payload serialization failed", ex);
        }
    }

    /**
     * 将 JSON 文本严格反序列化为调用方指定的 payload 类型。
     *
     * <p>未知字段、语法错误和不能映射到目标类型的 JSON 会失败；缺失字段是否允许取决于目标类型和后续业务校验。
     * 返回对象后，调用方仍必须校验 {@code type}、{@code version}、可空字段和资源范围，不能仅因反序列化成功就
     * 执行敏感操作。</p>
     *
     * @param payload JSON 文本，而非历史自由文本快照
     * @param payloadType 期望的具体 payload 类
     * @param <T> payload 的 Java 类型
     * @return 反序列化后的 payload；不会自动做版本迁移或业务字段校验
     * @throws IllegalArgumentException 非法 JSON、未知字段或 Jackson 映射失败时抛出，并保留原始异常作为原因
     */
    public static <T> T read(String payload, Class<T> payloadType) {
        try {
            return OBJECT_MAPPER.readValue(payload, payloadType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("approval payload deserialization failed", ex);
        }
    }
}
