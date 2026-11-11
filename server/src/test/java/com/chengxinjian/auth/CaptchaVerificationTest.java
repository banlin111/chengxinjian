package com.chengxinjian.auth;

import com.chengxinjian.common.BusinessException;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaptchaVerificationTest {
    AuthProperties auth(String mode){return new AuthProperties(Duration.ofHours(2),Duration.ofDays(30),mode,"local-ticket-for-testing","","","");}
    CaptchaProperties configured(){return new CaptchaProperties(123,"fixture-app-secret","fixture-secret-id","fixture-secret-key");}
    @Test void missingConfigAndFallbackTicketsNeverCallProvider() {
        var gateway=mock(CaptchaGateway.class);var consumption=mock(CaptchaConsumption.class);
        var service=new CaptchaVerification(auth("tencent"),new CaptchaProperties(0,"","",""),new MockEnvironment(),gateway,consumption);
        assertThatThrownBy(()->service.verify("ticket","rand","127.0.0.1")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("PROVIDER_UNAVAILABLE"));
        var configured=new CaptchaVerification(auth("tencent"),configured(),new MockEnvironment(),gateway,consumption);
        for(String ticket:new String[]{"trerror_fallback","trerror",""})assertThatThrownBy(()->configured.verify(ticket,"rand","127.0.0.1")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->configured.verify("ticket",null,"127.0.0.1")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(gateway,consumption);
    }
    @Test void localTicketCannotBeUsedOutsideLocalOrWithProd() {
        for(var env:new MockEnvironment[]{new MockEnvironment(),new MockEnvironment().withProperty("spring.profiles.active","local,prod")}) {
            var service=new CaptchaVerification(auth("local"),configured(),env,mock(CaptchaGateway.class),mock(CaptchaConsumption.class));
            assertThatThrownBy(()->service.verify("local-ticket-for-testing",null,"127.0.0.1")).isInstanceOf(BusinessException.class);
        }
    }
    @Test void claimPrecedesProviderAndExceptionsCannotFallBack() {
        var gateway=mock(CaptchaGateway.class);var consumption=mock(CaptchaConsumption.class);
        var service=new CaptchaVerification(auth("tencent"),configured(),new MockEnvironment(),gateway,consumption);
        doThrow(TencentCaptchaGateway.unavailable()).when(gateway).verify("ticket","random","127.0.0.1");
        assertThatThrownBy(()->service.verify("ticket","random","127.0.0.1")).isInstanceOf(BusinessException.class);
        var order=inOrder(consumption,gateway);order.verify(consumption).claim("ticket");order.verify(gateway).verify("ticket","random","127.0.0.1");
    }
    @Test void onlySuccessWithNoReportedMalicePasses() {
        var response=new DescribeCaptchaResultResponse();response.setCaptchaCode(1L);response.setEvilLevel(0L);TencentCaptchaGateway.check(response);
        for(long code:new long[]{0,7,8,9,15,16,21,100,999}) {
            response.setCaptchaCode(code);assertThatThrownBy(()->TencentCaptchaGateway.check(response)).isInstanceOf(BusinessException.class);
        }
        response.setCaptchaCode(1L);response.setEvilLevel(100L);assertThatThrownBy(()->TencentCaptchaGateway.check(response)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->TencentCaptchaGateway.check(new DescribeCaptchaResultResponse())).isInstanceOf(BusinessException.class);
    }
}
