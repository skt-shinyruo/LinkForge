package com.linkforge.redirect.interfaces.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class RedirectConfirmHrefBuilder {

    private static final int MAX_CONFIRM_PARAMS = 50;
    private static final int MAX_CONFIRM_VALUES_PER_PARAM = 5;
    private static final int MAX_CONFIRM_PARAM_NAME_LEN = 128;
    private static final int MAX_CONFIRM_VALUE_LEN = 256;
    private static final int MAX_CONFIRM_HREF_LEN = 4096;

    public String build(HttpServletRequest request) {
        String path = request == null ? null : request.getRequestURI();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        int added = 0;
        if (request != null) {
            Map<String, String[]> params = request.getParameterMap();
            if (params != null) {
                outer:
                for (Map.Entry<String, String[]> entry : params.entrySet()) {
                    if (added >= MAX_CONFIRM_PARAMS) {
                        break;
                    }
                    String name = entry.getKey();
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    if ("__lf_confirm".equals(name) || "__lf_preview".equals(name)) {
                        continue;
                    }
                    if (name.length() > MAX_CONFIRM_PARAM_NAME_LEN) {
                        continue;
                    }
                    String[] values = entry.getValue();
                    if (values == null || values.length == 0) {
                        builder.queryParam(name);
                        added++;
                        continue;
                    }
                    int valuesAdded = 0;
                    for (String value : values) {
                        if (added >= MAX_CONFIRM_PARAMS) {
                            break outer;
                        }
                        if (valuesAdded >= MAX_CONFIRM_VALUES_PER_PARAM) {
                            break;
                        }
                        if (value == null) {
                            builder.queryParam(name);
                        } else {
                            String truncated = value;
                            if (truncated.length() > MAX_CONFIRM_VALUE_LEN) {
                                truncated = truncated.substring(0, MAX_CONFIRM_VALUE_LEN);
                            }
                            builder.queryParam(name, truncated);
                        }
                        added++;
                        valuesAdded++;
                    }
                }
            }
        }
        builder.queryParam("__lf_confirm", "1");
        String href = builder.build().toUriString();
        if (href.length() > MAX_CONFIRM_HREF_LEN) {
            return UriComponentsBuilder.fromPath(path).queryParam("__lf_confirm", "1").build().toUriString();
        }
        return href;
    }
}
