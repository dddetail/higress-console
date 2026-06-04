/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console.service.oauth2;

import java.security.GeneralSecurityException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.higress.console.util.AesUtil;
import com.alibaba.higress.sdk.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * Encrypts and decrypts OAuth2 tokens for database storage.
 */
@Slf4j
@Component
public class TokenEncryptor {

    @Value("${higress-console.oauth2.token-encrypt-key:higress-oauth2-token-enc-key!!!!}")
    private String encryptKey;

    @Value("${higress-console.oauth2.token-encrypt-iv:higress-oauth2iv}")
    private String encryptIv;

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            return AesUtil.encrypt(encryptKey, encryptIv, plainText);
        } catch (GeneralSecurityException e) {
            throw new BusinessException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            return AesUtil.decrypt(encryptKey, encryptIv, cipherText);
        } catch (GeneralSecurityException e) {
            throw new BusinessException("Failed to decrypt token", e);
        }
    }
}
