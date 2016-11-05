package updater;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class MultipartFormDataUtil {
    private final String boundary;
    private static final String lineReturn = "\r\n";
    private HttpURLConnection conn;
    private DataOutputStream dos;
    int bytesRead, bytesAvail, bufferSize;
    byte[] buffer;
    int maxBufferSize = 1 * 1024 * 1024;
    List<String> response;

    public MultipartFormDataUtil(String postUrl, LinkedHashMap<String, String> params) throws IOException {
        boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.

        URL url = new URL(postUrl);
        conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.28 Safari/537.36");
        conn.setRequestProperty("Referer", postUrl);
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        conn.setRequestProperty("Cache-Control", "max-age=0");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary" + boundary);
        conn.setRequestProperty("Content-Length", getContentLength(params));
        conn.setRequestProperty("Cookie", "__cfduid=d8d4c36999855e389de94f515940ef7121463283582; ips4_IPSSessionFront=3a118037fe670166fe1b51cc777d6209; ips4_hasJS=true; ips4_member_id=52398; ips4_pass_hash=baec4fe50810aaecbd72bd546869ee08; ips4_ipsTimezone=America/Chicago; repo_sort=default; __utmt=1; __utma=23143679.1640161904.1463283588.1477961529.1477968973.1043; __utmb=23143679.92.10.1477968973; __utmc=23143679; __utmz=23143679.1477944745.1040.58.utmcsr=sythe.org|utmccn=(referral)|utmcmd=referral|utmcct=/threads/looking-for-a-private-tribot-combat-script/");
        conn.setRequestProperty("Origin", "https://tribot.org");
        conn.setDoOutput(true);

        dos = new DataOutputStream(conn.getOutputStream());

        for (String key : params.keySet()) {
            addFormPart(key, params.get(key));
        }
        finish();
    }

    public String getContentLength(LinkedHashMap<String, String> params) {
        String content = "";
        for (String name : params.keySet()) {
            String value = params.get(name);
            content += "------WebKitFormBoundary" + boundary + lineReturn;
            content += "Content-Disposition: form-data; name=\"" + name + "\"" + lineReturn;
            content += value + lineReturn;
        }

        content += "------WebKitFormBoundary" + boundary + "--" + lineReturn;

        return content.length() + "";
    }

    public MultipartFormDataUtil(String postUrl, LinkedHashMap<String, String> params, File file) throws IOException {
        boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.

        URL url = new URL(postUrl);
        conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.28 Safari/537.36");
        conn.setRequestProperty("Referer", postUrl);
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        conn.setRequestProperty("Cache-Control", "max-age=0");
        conn.setRequestProperty("Content-Length", file.length() + "");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary" + boundary);
        conn.setRequestProperty("Cookie", "__cfduid=d8d4c36999855e389de94f515940ef7121463283582; ips4_IPSSessionFront=3a118037fe670166fe1b51cc777d6209; ips4_hasJS=true; ips4_member_id=52398; ips4_pass_hash=baec4fe50810aaecbd72bd546869ee08; ips4_ipsTimezone=America/Chicago; repo_sort=default; __utmt=1; __utma=23143679.1640161904.1463283588.1477961529.1477968973.1043; __utmb=23143679.92.10.1477968973; __utmc=23143679; __utmz=23143679.1477944745.1040.58.utmcsr=sythe.org|utmccn=(referral)|utmcmd=referral|utmcct=/threads/looking-for-a-private-tribot-combat-script/");
        conn.setRequestProperty("Origin", "https://tribot.org");
        conn.setDoOutput(true);

        dos = new DataOutputStream(conn.getOutputStream());

        for (String key : params.keySet()) {
            addFormPart(key, params.get(key));
        }

        addFilePart(file);

        finish();
    }

    private void addFormPart(String name, String value) throws IOException {
        dos.writeBytes("------WebKitFormBoundary" + boundary + lineReturn);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + lineReturn);
        dos.writeBytes("Content-Type: text/plain" + lineReturn + lineReturn);
        dos.writeBytes(value + lineReturn);
        dos.flush();
    }

    private void addFilePart(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);

        dos.writeBytes("------WebKitFormBoundary" + boundary + lineReturn);
        dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + lineReturn);
        dos.writeBytes("Content-Type: " + URLConnection.guessContentTypeFromName(file.getName()) + lineReturn);
        dos.writeBytes("Content-Transfer-Encoding: binary" + lineReturn + lineReturn);

        bytesAvail = fileInputStream.available();
        bufferSize = Math.min(bytesAvail, maxBufferSize);
        buffer = new byte[bufferSize];

        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

        while (bytesRead > 0) {
            dos.write(buffer, 0, bufferSize);
            bytesAvail = fileInputStream.available();
            bufferSize = Math.min(bytesAvail, maxBufferSize);
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        }
        dos.flush();

        dos.writeBytes(lineReturn);
        dos.flush();
        fileInputStream.close();
    }

    private void finish() throws IOException {
        response = new ArrayList<String>();

        dos.writeBytes("------WebKitFormBoundary" + boundary + "--" + lineReturn);
        dos.flush();
        dos.close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;

        while ((line = reader.readLine()) != null) {
            response.add(line);
        }

        reader.close();
        conn.disconnect();
    }

    public List<String> getResponse() {
        return response;
    }
}