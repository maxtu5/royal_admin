package com.tuiken.royaladmin.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class CsvUploadService {

    @Value("${google.secretpath}")
    private String SERVICE_ACOUNT_KEY_PATH;
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
        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(SERVICE_ACOUNT_KEY_PATH))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .build();
    }

}
