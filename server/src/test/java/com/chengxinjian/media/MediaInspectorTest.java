package com.chengxinjian.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MediaInspectorTest {
    @TempDir Path temp;
    MediaInspector inspector=new MediaInspector(new MediaProperties("","","","","","",1000000,14400,2,false));
    byte[] image() throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void decodedImageReturnsNoInventedDuration() throws Exception {
        byte[] bytes=image();var file=temp.resolve("image");Files.write(file,bytes);assertThat(inspector.inspect(file,"image/png",bytes.length,hash(bytes))).isNull();
    }
    @Test void rejectsMimeSpoofAndHashOrSizeMismatch() throws Exception {
        byte[] bytes=image();var file=temp.resolve("image");Files.write(file,bytes);
        assertThatThrownBy(()->inspector.inspect(file,"image/jpeg",bytes.length,hash(bytes))).isInstanceOf(MediaErrors.Invalid.class);
        assertThatThrownBy(()->inspector.inspect(file,"image/png",bytes.length,"a".repeat(64))).isInstanceOf(MediaErrors.Invalid.class);
        assertThatThrownBy(()->inspector.inspect(file,"image/png",bytes.length+1,hash(bytes))).isInstanceOf(MediaErrors.Invalid.class);
    }
    @Test void magicHeaderAloneDoesNotPassImageDecoding() throws Exception {
        byte[] bytes=image();bytes=java.util.Arrays.copyOf(bytes,20);var file=temp.resolve("truncated");Files.write(file,bytes);String hash=hash(bytes);int size=bytes.length;
        assertThatThrownBy(()->inspector.inspect(file,"image/png",size,hash)).isInstanceOf(MediaErrors.Invalid.class);
    }
    @Test void absentDecoderIsUnavailableAndNeverMarksAudioReady() throws Exception {
        byte[] bytes=new byte[44];System.arraycopy("RIFF".getBytes(),0,bytes,0,4);System.arraycopy("WAVE".getBytes(),0,bytes,8,4);
        var file=temp.resolve("audio");Files.write(file,bytes);
        assertThatThrownBy(()->inspector.inspect(file,"audio/wav",bytes.length,hash(bytes))).isInstanceOf(com.chengxinjian.common.BusinessException.class);
    }
    @Test void jpegAndWebpAreDecodedAndTruncatedJpegIsRejected() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpeg",out);byte[] jpeg=out.toByteArray();
        var file=temp.resolve("jpeg");Files.write(file,jpeg);assertThat(inspector.inspect(file,"image/jpeg",jpeg.length,hash(jpeg))).isNull();
        byte[] truncated=java.util.Arrays.copyOf(jpeg,jpeg.length-2);Files.write(file,truncated);
        assertThatThrownBy(()->inspector.inspect(file,"image/jpeg",truncated.length,hash(truncated))).isInstanceOf(MediaErrors.Invalid.class);
        byte[] webp;try(var input=getClass().getResourceAsStream("/media/fixture.webp")){webp=input.readAllBytes();}
        var webpFile=temp.resolve("webp");Files.write(webpFile,webp);assertThat(inspector.inspect(webpFile,"image/webp",webp.length,hash(webp))).isNull();
    }
}
