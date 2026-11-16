package com.chengxinjian.user;

import com.chengxinjian.auth.CaptchaConsumption;
import com.tencentcloudapi.captcha.v20190722.CaptchaClient;
import com.tencentcloudapi.captcha.v20190722.models.DescribeCaptchaMiniResultRequest;
import com.chengxinjian.common.BusinessException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.*;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(PhoneProviderProperties.class)
public class PhoneProviders {
    private final PhoneProviderProperties config;
    private final CaptchaConsumption tickets;
    private final okhttp3.OkHttpClient http = new okhttp3.OkHttpClient.Builder()
        .connectTimeout(3,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).followRedirects(false)
        .followSslRedirects(false).retryOnConnectionFailure(false).build();
    PhoneProviders(PhoneProviderProperties config,CaptchaConsumption tickets) { this.config=config; this.tickets=tickets; }
    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"PROVIDER_UNAVAILABLE","短信验证服务暂不可用");
    }
    private ClientProfile clientProfile(String endpoint) {
        var settings = new HttpProfile();
        settings.setProtocol("https://");
        settings.setEndpoint(endpoint);
        settings.setHttpClient(http);
        var profile = new ClientProfile();
        profile.setHttpProfile(settings);
        profile.setDebug(false);
        return profile;
    }
    public void checkCaptcha(String ticket, String ip) {
        if (!config.smsConfigured() || config.captchaAppId() <= 0 || config.captchaAppSecret().isBlank()
                || config.captchaSecretId().isBlank() || config.captchaSecretKey().isBlank()) throw unavailable();
        if (ticket.startsWith("trerror")) throw BusinessException.invalid("请重新完成人机验证");
        tickets.claim(ticket);
        var client = new CaptchaClient(new Credential(config.captchaSecretId(), config.captchaSecretKey()),
            "", clientProfile("captcha.tencentcloudapi.com"));
        var request = new DescribeCaptchaMiniResultRequest();
        request.setCaptchaType(9L);
        request.setTicket(ticket);
        request.setUserIp(ip);
        request.setCaptchaAppId(config.captchaAppId());
        request.setAppSecretKey(config.captchaAppSecret());
        try {
            var result = client.DescribeCaptchaMiniResult(request);
            Long code = result == null ? null : result.getCaptchaCode();
            if (code == null || code == 16 || code == 26 || code == 31 || code == 100) throw unavailable();
            if (code != 1) throw BusinessException.invalid("请重新完成人机验证");
        } catch (TencentCloudSDKException ex) {
            throw unavailable();
        }
    }
    public void send(String phone,String code) {
        if (!config.smsConfigured()) throw unavailable();
        var profile = clientProfile("sms.tencentcloudapi.com");
        var client=new SmsClient(new Credential(config.secretId(),config.secretKey()),config.region(),profile);
        var request=new SendSmsRequest();
        request.setSmsSdkAppId(config.smsAppId()); request.setPhoneNumberSet(new String[]{"+86"+phone});
        request.setSignName(config.sign()); request.setTemplateId(config.templateId()); request.setTemplateParamSet(new String[]{code});
        try {
            var result=client.SendSms(request).getSendStatusSet();
            if (result==null || result.length!=1 || result[0]==null || !"Ok".equals(result[0].getCode())
                    || !("+86"+phone).equals(result[0].getPhoneNumber())) throw unavailable();
        } catch (TencentCloudSDKException ex) { throw unavailable(); }
    }
}
