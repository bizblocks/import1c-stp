package com.groupstp.import1cstp.service;

import com.google.gson.JsonElement;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Service(Sync1CService.NAME)
public class Sync1CServiceBean implements Sync1CService {

    @Override
    public JsonElement getData1C(String url, String userpass) throws IOException, NoSuchAlgorithmException {
        return this.getData1C(url, userpass, new HashMap<>());
    }

    @Override
    public JsonElement getData1C(String url, String userpass, HashMap<String, String> params) throws IOException, NoSuchAlgorithmException {
        URL u = new URL(url);
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setRequestMethod("GET");
        params.put("hash", passHash(userpass));
        for (Map.Entry<String,String> e: params.entrySet()) {
            con.setRequestProperty(e.getKey(), e.getValue());
        }
        con.setDoInput(true);
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        com.google.gson.JsonParser p = new com.google.gson.JsonParser();
        return p.parse(in);
    }

    private static String passHash(String pass) throws NoSuchAlgorithmException {
        MessageDigest crypt = MessageDigest.getInstance("MD5");
        return arr2Hex(crypt.digest(pass.getBytes()));
    }

    private static String arr2Hex(byte[] a)
    {
        StringBuilder res = new StringBuilder();
        for(byte num:a)
        {
            if(num<0)
                num = (byte) (num + 0x100);
            res.append(String.format("%02x", num));
        }
        return res.toString();
    }
}