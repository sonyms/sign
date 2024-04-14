package com.adobe.sign.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.adobe.sign.service.AdobeSignService;
import com.adobe.sign.util.Util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class AdobeSignController {

    @Autowired
    private AdobeSignService adobeSignService;

    @GetMapping("/")
    public String index() {
        return "upload";
    }

    @PostMapping("/upload")
    public ModelAndView uploadDocument(@RequestParam("document") MultipartFile document, HttpSession session)
            throws Exception {
        String transientDocumentId = adobeSignService.uploadDocument(document);
        String agreementId = adobeSignService.createAgreement(transientDocumentId);
        Util.storeAgreementIdInSession(session, agreementId);
        String signUrl = adobeSignService.getSigningUrl(agreementId);
        Util.getAgreementIdFromSession(session);
        return new ModelAndView("redirect:" + signUrl);
    }

    @GetMapping("/process-signed-document")
    public ResponseEntity<byte[]> processSignedDocument(HttpSession session) throws Exception {
        String agreementId = Util.getAgreementIdFromSession(session);
        if (agreementId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Or handle this case as you see fit
        }

        byte[] signedDocument = adobeSignService.downloadSignedDocument(agreementId);

        // Set headers to prompt the user for downloading the file
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // To ensure the download function works across all browsers
        String filename = "signedDocument.pdf";
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(signedDocument, headers, HttpStatus.OK);
    }
}
