package com.linkforge.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.accounts.interfaces.web.ApiKeyAdminController;
import com.linkforge.accounts.interfaces.web.AuthController;
import com.linkforge.accounts.interfaces.web.MeController;
import com.linkforge.analytics.interfaces.web.StatsController;
import com.linkforge.governance.interfaces.web.ApprovalController;
import com.linkforge.governance.interfaces.web.AuditController;
import com.linkforge.platform.interfaces.web.TenantAdminApplicationController;
import com.linkforge.platform.interfaces.web.TenantAdminDomainController;
import com.linkforge.shortlink.interfaces.web.ShortLinkController;
import com.linkforge.shortlink.interfaces.web.TagController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 前端消费的 method/path 快照必须仍然存在于后端 controller 映射中。 */
class WebApiContractSnapshotTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AuthController.class,
            MeController.class,
            ApiKeyAdminController.class,
            TenantAdminApplicationController.class,
            TenantAdminDomainController.class,
            ShortLinkController.class,
            TagController.class,
            StatsController.class,
            ApprovalController.class,
            AuditController.class
    );

    @Test
    void frontendSnapshot_shouldBeBackedByControllerMappings() throws IOException {
        Snapshot snapshot = new ObjectMapper().readValue(
                locateSnapshot().toFile(),
                Snapshot.class
        );
        Set<String> expected = snapshot.endpoints().stream()
                .map(endpoint -> key(endpoint.get(0), endpoint.get(1)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> actual = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            actual.addAll(mappedEndpoints(controller));
        }

        assertThat(actual).containsAll(expected);
    }

    private static Path locateSnapshot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path snapshot = candidate.resolve("contracts/web-api-v1.snapshot.json");
            if (Files.isRegularFile(snapshot)) {
                return snapshot;
            }
        }
        throw new IllegalStateException("contracts/web-api-v1.snapshot.json not found from " + current);
    }

    private static Set<String> mappedEndpoints(Class<?> controller) {
        RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
        List<String> bases = paths(
                classMapping == null ? null : classMapping.path(),
                classMapping == null ? null : classMapping.value(),
                "/"
        );
        Set<String> endpoints = new LinkedHashSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            Mapping mapping = mapping(method);
            if (mapping == null) {
                continue;
            }
            for (String base : bases) {
                for (String path : mapping.paths()) {
                    endpoints.add(key(mapping.method(), join(base, path)));
                }
            }
        }
        return endpoints;
    }

    private static Mapping mapping(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return new Mapping("GET", paths(get.path(), get.value(), "").toArray(String[]::new));
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return new Mapping("POST", paths(post.path(), post.value(), "").toArray(String[]::new));
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            return new Mapping("PUT", paths(put.path(), put.value(), "").toArray(String[]::new));
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            return new Mapping("DELETE", paths(delete.path(), delete.value(), "").toArray(String[]::new));
        }
        RequestMapping request = method.getAnnotation(RequestMapping.class);
        if (request == null || request.method().length == 0) {
            return null;
        }
        return new Mapping(
                request.method()[0].name(),
                paths(request.path(), request.value(), "").toArray(String[]::new)
        );
    }

    private static List<String> paths(String[] path, String[] value, String fallback) {
        if (path != null && path.length > 0) {
            return Arrays.asList(path);
        }
        if (value != null && value.length > 0) {
            return Arrays.asList(value);
        }
        return List.of(fallback);
    }

    private static String join(String base, String path) {
        String left = base == null || base.isBlank() ? "" : base;
        String right = path == null ? "" : path;
        String joined = (left + "/" + right).replaceAll("/{2,}", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    private static String key(String method, String path) {
        return method.toUpperCase() + " " + path.replaceAll("\\{[^}]+}", "{}");
    }

    private record Mapping(String method, String[] paths) {
    }

    private record Snapshot(int version, List<List<String>> endpoints) {
    }
}
