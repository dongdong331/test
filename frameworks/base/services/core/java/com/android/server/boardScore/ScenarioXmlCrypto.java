/*
 * ScenarioXmlDecrypt.java
 *
 * Copyright (C) 2016 Spreadtrum Communications Inc.
 */
package com.android.server.boardScore;
import android.util.Log;
import android.util.Base64;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.File;

import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;

public class ScenarioXmlCrypto{
    private static final String TAG = "ScenarioXmlDecrypt";
    private static final String TAGPATH = "/data/scenario_performance_power.xml";
    private static final String CRYPTOMODE = "AES";
    private SecretKeySpec key;


    public  ScenarioXmlCrypto(String strKey){
        getKey(strKey);
    }

    protected void getKey(String strKey) {
        try {
            byte[] enCodeFormat = strKey.getBytes();
            this.key = new SecretKeySpec(enCodeFormat , CRYPTOMODE);
        } catch (Exception e) {
            throw new RuntimeException(
                        "Error initializing SqlMap class. Cause: " + e);
        }
    }


    static InputStream deCrypto(String file, String aesCode) throws Exception {
        byte[] enCodeFormat = aesCode.getBytes();
        SecretKeySpec mKey = new SecretKeySpec(enCodeFormat , CRYPTOMODE);
        Cipher cipher = Cipher.getInstance(CRYPTOMODE);
        cipher.init(Cipher.DECRYPT_MODE, mKey);
        InputStream is =new BufferedInputStream(new FileInputStream(file));
        CipherInputStream cos = new CipherInputStream(is, cipher);
        return cos;
    }
    protected void enCrypto(String file)throws Exception{
        Cipher cipher = Cipher.getInstance(CRYPTOMODE);
        cipher.init(Cipher.ENCRYPT_MODE, this.key);
        InputStream is = new FileInputStream(file);
        File outFile = new File(TAGPATH);
        OutputStream out = new FileOutputStream(outFile);
        CipherInputStream cis = new CipherInputStream(is, cipher);
        byte[] buffer = new byte[256];
        int r;
        while ((r = cis.read(buffer)) > 0) {
            out.write(buffer, 0, r);
        }
        cis.close();
        is.close();
        out.close();
    }
}
