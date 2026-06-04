/*
 * Copyright (c) 2022-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SPA routing filter that ensures browser navigation requests are served the SPA index.html
 * instead of being handled by API controllers that share the same URL paths.
 *
 * <p>Problem: Frontend routes like /user/list overlap with API endpoints like GET /user/list.
 * When a user refreshes the page, Spring MVC routes the request to the controller which
 * returns raw JSON instead of the SPA page.</p>
 *
 * <p>Solution: Intercept browser navigation requests (identified by Accept: text/html header)
 * and forward them to /index.html so the SPA router can handle them client-side.</p>
 */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class SpaRoutingFilter implements Filter {

    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
        "/index.html", "/landing", "/healthz"
    ));

    private static final Set<String> STATIC_RESOURCE_PREFIXES = new HashSet<>(Arrays.asList(
        "/assets/", "/css/", "/js/", "/img/", "/images/", "/fonts/", "/static/", "/vs/"
    ));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (isBrowserNavigation(httpRequest) && !isExcluded(httpRequest)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isBrowserNavigation(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (EXCLUDED_PATHS.contains(path)) {
            return true;
        }

        for (String prefix : STATIC_RESOURCE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
