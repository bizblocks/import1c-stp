package com.groupstp.import1cstp.service;


import com.google.gson.JsonElement;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public interface Sync1CService {
    String NAME = "import1cstp_Sync1CService";
    JsonElement getData1C(String url, String userpass) throws IOException, NoSuchAlgorithmException;
    JsonElement getData1C(String url, String userpass, HashMap<String, String> params) throws IOException, NoSuchAlgorithmException;
    JsonElement getData1C(String url, String userpass, String user, String body) throws IOException, NoSuchAlgorithmException;
}