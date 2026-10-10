package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.interceptor.RoleInterceptor;
import com.olehprukhnytskyi.macrotrackeruserservice.service.WebBillingAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class WebBillingAdminAuthorizationTest {
    private final WebBillingAdminController controller = new WebBillingAdminController(
            mock(WebBillingAdminService.class));
    private final RoleInterceptor interceptor = new RoleInterceptor();

    @Test
    void everyAdminEndpointRejectsMissingOrRegularRoleThroughExistingInterceptor() {
        for (var method : WebBillingAdminController.class.getDeclaredMethods()) {
            var handler = new HandlerMethod(controller, method);
            for (String roles : new String[] {null, "USER", "VIP", "ADMINISTRATOR"}) {
                var request = new MockHttpServletRequest();
                if (roles != null) {
                    request.addHeader("X-User-Roles", roles);
                }
                assertThatThrownBy(() -> interceptor.preHandle(request,
                        new MockHttpServletResponse(), handler))
                        .isInstanceOf(BadRequestException.class);
            }
        }
    }

    @Test
    void verifiedGatewayAdminRolePassesAllAdminEndpoints() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "USER, ADMIN");
        for (var method : WebBillingAdminController.class.getDeclaredMethods()) {
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(),
                    new HandlerMethod(controller, method))).isTrue();
        }
    }
}
