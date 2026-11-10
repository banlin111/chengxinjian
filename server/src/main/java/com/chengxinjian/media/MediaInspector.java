package com.chengxinjian.media;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class MediaInspector {
    private final MediaProperties properties;
    public MediaInspector(MediaProperties properties) { this.properties=properties; }
    public Integer inspect(Path file,String mime,long size,String hash) {
        try {
            if (Files.size(file)!=size) throw new MediaErrors.Invalid("SIZE_MISMATCH");
            var digest=MessageDigest.getInstance("SHA-256");
            try (var input=Files.newInputStream(file)) {
                byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1) digest.update(buffer,0,n);
            }
            if (!hash.equals(HexFormat.of().formatHex(digest.digest()))) throw new MediaErrors.Invalid("SHA256_MISMATCH");
            byte[] header;try(var input=Files.newInputStream(file)) { header=input.readNBytes(16); }
            if (mime.startsWith("image/")) { image(file,mime,header,size);return null; }
            magic(mime,header);
            return decode(file,mime);
        } catch (MediaErrors.Invalid e) { throw e; }
        catch (com.chengxinjian.common.BusinessException e) { throw e; }
        catch (Exception e) { throw MediaErrors.unavailable(); }
    }
    private boolean prefix(byte[] bytes,int... expected) {
        if(bytes.length<expected.length)return false;
        for(int i=0;i<expected.length;i++) if((bytes[i]&255)!=expected[i])return false;return true;
    }
    private String ascii(byte[] bytes,int from,int length) { return bytes.length>=from+length ? new String(bytes,from,length,StandardCharsets.US_ASCII) : ""; }
    private void magic(String mime,byte[] header) {
        boolean valid=switch(mime) {
            case "audio/wav" -> ascii(header,0,4).equals("RIFF") && ascii(header,8,4).equals("WAVE");
            case "audio/mp4","video/mp4" -> ascii(header,4,4).equals("ftyp");
            case "video/webm" -> prefix(header,0x1a,0x45,0xdf,0xa3);
            case "audio/mpeg" -> ascii(header,0,3).equals("ID3") || (header.length>1 && (header[0]&255)==255 && (header[1]&224)==224);
            default -> false;
        };
        if(!valid)throw new MediaErrors.Invalid("MEDIA_TYPE_MISMATCH");
    }
    private void image(Path file,String mime,byte[] header,long size) {
        boolean valid=switch(mime) {
            case "image/png" -> prefix(header,137,80,78,71,13,10,26,10);
            case "image/jpeg" -> prefix(header,255,216,255);
            case "image/webp" -> ascii(header,0,4).equals("RIFF") && ascii(header,8,4).equals("WEBP") && Integer.toUnsignedLong(ByteBuffer.wrap(header,4,4).order(ByteOrder.LITTLE_ENDIAN).getInt())+8==size;
            default -> false;
        };
        if(!valid)throw new MediaErrors.Invalid("MEDIA_TYPE_MISMATCH");
        try(var channel=java.nio.channels.FileChannel.open(file)) {
            int count="image/jpeg".equals(mime) ? 2 : "image/png".equals(mime) ? 8 : 0;
            if(count>0) {
                if(size<count)throw new MediaErrors.Invalid("IMAGE_DECODE_FAILED");
                var tail=ByteBuffer.allocate(count);channel.position(size-count);while(tail.hasRemaining() && channel.read(tail)!=-1) {}
                byte[] expected="image/jpeg".equals(mime) ? new byte[]{(byte)255,(byte)217} : new byte[]{73,69,78,68,(byte)174,66,96,(byte)130};
                if(!java.util.Arrays.equals(tail.array(),expected))throw new MediaErrors.Invalid("IMAGE_DECODE_FAILED");
            }
        } catch(IOException e) { throw MediaErrors.unavailable(); }
        try (var input=ImageIO.createImageInputStream(file.toFile())) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new MediaErrors.Invalid("IMAGE_DECODE_FAILED");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);long width=reader.getWidth(0),height=reader.getHeight(0);
                reader.addIIOReadWarningListener((source,warning)->{throw new MediaErrors.Invalid("IMAGE_DECODE_FAILED");});
                if(width<=0 || height<=0 || width*height>16_000_000)throw new MediaErrors.Invalid("IMAGE_DIMENSIONS_EXCEEDED");
                if(reader.read(0)==null)throw new MediaErrors.Invalid("IMAGE_DECODE_FAILED");
            } finally { reader.dispose(); }
        } catch (MediaErrors.Invalid e) { throw e; }
        catch (IOException|RuntimeException e) { throw new MediaErrors.Invalid("IMAGE_DECODE_FAILED"); }
    }
    private Integer decode(Path file,String mime) throws IOException,InterruptedException {
        if(properties.ffmpegPath()==null || properties.ffmpegPath().isBlank() || !Files.isExecutable(Path.of(properties.ffmpegPath()))) throw MediaErrors.unavailable();
        Path progress=Files.createTempFile("cxj-progress-",".txt"),errors=Files.createTempFile("cxj-decode-",".txt");Process process=null;
        try {
            var builder=new ProcessBuilder(properties.ffmpegPath(),"-hide_banner","-nostdin","-nostats","-max_alloc","67108864",
                "-timelimit",Integer.toString(properties.decodeTimeoutSeconds()),"-max_pixels","16000000",
                "-protocol_whitelist","file,pipe","-format_whitelist","mov,mp4,m4a,3gp,3g2,mj2,mp3,wav,matroska,webm",
                "-threads","1","-err_detect","explode","-xerror","-i",file.toAbsolutePath().toString(),
                "-map","0:a?","-map","0:V?","-sn","-dn","-threads","1","-filter_threads","1","-filter_complex_threads","1",
                "-progress","pipe:1","-f","null","-").redirectOutput(progress.toFile()).redirectError(errors.toFile());
            // Native decoding must not inherit database, WeChat or cloud credentials.
            builder.environment().clear();builder.environment().put("LANG","C");process=builder.start();
            if(!process.waitFor(properties.decodeTimeoutSeconds(),TimeUnit.SECONDS)) {
                process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new MediaErrors.Invalid("MEDIA_DECODE_TIMEOUT");
            }
            if(Files.size(errors)>262144 || Files.size(progress)>262144 || process.exitValue()!=0)throw new MediaErrors.Invalid("MEDIA_DECODE_FAILED");
            String details=Files.readString(errors);boolean audio=Pattern.compile("Stream #.*Audio:").matcher(details).find();
            boolean video=details.lines().anyMatch(line->line.contains("Stream #") && line.contains("Video:") && !line.contains("(attached pic)"));
            if(mime.startsWith("audio/") ? !audio || video : !video)throw new MediaErrors.Invalid("MEDIA_STREAM_MISMATCH");
            long micros=0;for(String line:Files.readAllLines(progress)) if(line.startsWith("out_time_us=")) {
                try { micros=Math.max(micros,Long.parseLong(line.substring(12))); } catch(NumberFormatException ignored) {}
            }
            if(micros<=0)throw new MediaErrors.Invalid("MEDIA_DURATION_INVALID");
            long seconds=(micros+999999)/1_000_000;
            if(seconds>properties.maxDurationSeconds())throw new MediaErrors.Invalid("MEDIA_DURATION_EXCEEDED");
            return (int)seconds;
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw MediaErrors.unavailable(); }
        finally {
            if(process!=null && process.isAlive())process.destroyForcibly();Files.deleteIfExists(progress);Files.deleteIfExists(errors);
        }
    }
}
