package com.chengxinjian.auth;

import com.chengxinjian.common.BusinessException;
import com.tencentcloudapi.captcha.v20190722.CaptchaClient;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultRequest;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaResultResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(CaptchaProperties.class)
public class TencentCaptchaGateway implements CaptchaGateway {
    private final CaptchaProperties properties;
    public TencentCaptchaGateway(CaptchaProperties properties) { this.properties=properties; }
    static BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","验证码服务暂不可用，请稍后重新验证");
    }
    @Override public void verify(String ticket,String randstr,String remoteAddress) {
        if (!properties.configured()) throw unavailable();
        var request=new DescribeCaptchaResultRequest();
        request.setCaptchaType(9L); request.setCaptchaAppId(properties.appId()); request.setAppSecretKey(properties.appSecretKey());
        request.setTicket(ticket); request.setRandstr(randstr); request.setUserIp(remoteAddress);
        var http=new HttpProfile(); http.setEndpoint("captcha.tencentcloudapi.com"); http.setProtocol("https://");
        http.setConnTimeout(3); http.setReadTimeout(5); http.setWriteTimeout(5);
        http.setHttpClient(new okhttp3.OkHttpClient.Builder().connectTimeout(3,java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5,java.util.concurrent.TimeUnit.SECONDS).writeTimeout(5,java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(8,java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build());
        var profile=new ClientProfile(); profile.setHttpProfile(http); profile.setDebug(false);
        try {
            var client=new CaptchaClient(new Credential(properties.secretId(),properties.secretKey()),"",profile);
            check(client.DescribeCaptchaResult(request));
        } catch (TencentCloudSDKException ex) {
            // Never attach the SDK exception: it may include request/provider data.
            throw unavailable();
        }
    }
    static void check(DescribeCaptchaResultResponse response) {
        if (response==null || response.getCaptchaCode()==null) throw unavailable();
        long code=response.getCaptchaCode();
        if (code==16 || code==100) throw unavailable();
        if (code!=1 || (response.getEvilLevel()!=null && response.getEvilLevel()!=0))
            throw BusinessException.invalid("验证码校验失败，请重新验证");
    }
}
