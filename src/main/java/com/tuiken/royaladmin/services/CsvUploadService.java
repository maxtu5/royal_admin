package com.tuiken.royaladmin.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class CsvUploadService {

    private static final String TOKENS_DIRECTORY_PATH = "C:\\Users\\MT\\IdeaProjects\\royal_admin\\data\\tokens";
    @Value("${google.secretpath}")
    private String SERVICE_ACOUNT_KEY_PATH ="C:\\Users\\MT\\IdeaProjects\\royal_admin\\src\\main\\resources\\secretgoogle.json";
    @Value("${google.sourcepath}")
    private String SOURCE_PATH;
    private final String FOLDER_ID = "1etrTJpvWZdISB-RZuKJmibVs0reKJHLB";
    private final String GOOGLE_DIRECT_LINK = "https://drive.google.com/uc?export=download&id=%s";


    public String uploadToDrive(String fileName) {
        File file = new File(SOURCE_PATH+fileName);
        try{
            Drive drive = createDriveService();
            com.google.api.services.drive.model.File fileMetaData = new com.google.api.services.drive.model.File();
            fileMetaData.setName(file.getName());
            fileMetaData.setParents(Collections.singletonList(FOLDER_ID));
            FileContent mediaContent = new FileContent("text/csv", file);
            com.google.api.services.drive.model.File uploadedFile = drive.files().create(fileMetaData, mediaContent)
                    .setFields("id").execute();
            System.out.println("Uploaded to google drive: " + fileName);
            System.out.println(String.format(GOOGLE_DIRECT_LINK, uploadedFile.getId()));
            return String.format(GOOGLE_DIRECT_LINK, uploadedFile.getId());
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    private Drive createDriveService() throws GeneralSecurityException, IOException {
        FileInputStream fileInputStream = new FileInputStream(SERVICE_ACOUNT_KEY_PATH);
        if (fileInputStream == null) {
            throw new FileNotFoundException("Resource not found: " + SERVICE_ACOUNT_KEY_PATH);
        }
        JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        List<String> SCOPES =
                Collections.singletonList(DriveScopes.DRIVE_METADATA_READONLY);

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(fileInputStream));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
//                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return new Drive.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                credential)
                .setApplicationName("APPLICATION_NAME")
                .build();
    }

}
