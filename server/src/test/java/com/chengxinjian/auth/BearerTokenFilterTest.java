package com.chengxinjian.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BearerTokenFilterTest {
    @Test void databaseOutageReturns503WithoutLeakingConnectionDetails() throws Exception {
        var auth = mock(AdminAuthService.class);
        when(auth.authenticate("opaque-token")).thenThrow(new DataAccessResourceFailureException("sensitive-connection-details"));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var filter = new BearerTokenFilter(auth, mock(com.chengxinjian.user.UserSessionService.class), mapper);
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
        request.addHeader("Authorization", "Bearer opaque-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res) -> { throw new AssertionError("Protected request must stop on authentication failure"); });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mapper.readTree(response.getContentAsByteArray()).get("code").asText()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.getContentAsString()).doesNotContain("sensitive-connection-details");
    }
}
