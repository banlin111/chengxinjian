package com.chengxinjian;

import com.chengxinjian.auth.CaptchaGateway;
import com.chengxinjian.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:captcha;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
    "app.auth.bootstrap-username=integration-admin","app.auth.bootstrap-password=integration-password-12345",
    "app.auth.local-captcha-ticket=integration-captcha-ticket-12345","app.auth.captcha-mode=tencent",
    "app.captcha.app-id=123","app.captcha.app-secret-key=fixture","app.captcha.secret-id=fixture","app.captcha.secret-key=fixture"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class CaptchaIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@MockitoBean CaptchaGateway gateway;
    @BeforeEach void setup(){jdbc.update("DELETE FROM captcha_consumption");jdbc.update("DELETE FROM login_throttle");jdbc.update("UPDATE admin_account SET enabled=TRUE WHERE username='integration-admin'");}
    int login(String ticket,String password)throws Exception {
        return mvc.perform(post("/api/v1/admin/auth/login").with(r->{r.setRemoteAddr("192.0.2.10");return r;})
            .header("X-Forwarded-For","203.0.113.10").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "username","integration-admin","password",password,"captchaTicket",ticket,"captchaRandstr","random")))).andReturn().getResponse().getStatus();
    }
    @Test void concurrentTicketCanOnlyCreateOneSessionAndProviderRunsOutsideTransaction()throws Exception {
        doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return null;}).when(gateway).verify(anyString(),anyString(),anyString());
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=List.<java.util.concurrent.Callable<Integer>>of(()->login("same-ticket","integration-password-12345"),()->login("same-ticket","integration-password-12345"));
            var statuses=new ArrayList<Integer>();for(var f:pool.invokeAll(tasks))statuses.add(f.get());assertThat(statuses).containsExactlyInAnyOrder(200,400);
        }
        verify(gateway,times(1)).verify("same-ticket","random","192.0.2.10");
        assertThat(jdbc.queryForObject("SELECT ticket_hash FROM captcha_consumption",String.class)).hasSize(64).doesNotContain("same-ticket");
    }
    @Test void providerFailureAndWrongPasswordCannotReuseTicket()throws Exception {
        doThrow(new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","unavailable")).when(gateway).verify(eq("provider-failure"),anyString(),anyString());
        assertThat(login("provider-failure","integration-password-12345")).isEqualTo(503);
        assertThat(login("provider-failure","integration-password-12345")).isEqualTo(400);
        assertThat(login("wrong-password","invalid-password-12345")).isEqualTo(401);
        assertThat(login("wrong-password","integration-password-12345")).isEqualTo(400);
    }
}
