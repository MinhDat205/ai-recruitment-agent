package com.recruitment.cvbuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// FR-U12 R-P2 - hai font Noto Sans ban TINH (nguon: L23), nam trong classpath fonts/ kem OFL.txt. Doc bytes MOT lan luc
// khoi dong; thieu file -> khoi dong loi, log neu ten file thieu. Khong tai gi tu mang, khong dung font he thong.
// Moi tai lieu PDF nap font rieng (PDType0Font gan voi PDDocument va co cache khong an toan da luong) - bytes thi dung chung.
@Component
public class CvPdfFonts {

    private static final Logger log = LoggerFactory.getLogger(CvPdfFonts.class);

    static final String DIRECTORY = "fonts/";
    static final String REGULAR_FILE = "NotoSans-Regular.ttf";
    static final String BOLD_FILE = "NotoSans-Bold.ttf";

    private final byte[] regular;
    private final byte[] bold;

    @Autowired
    public CvPdfFonts() {
        this(DIRECTORY);
    }

    // Test: thu muc khong ton tai -> loi khoi dong.
    CvPdfFonts(String directory) {
        this.regular = read(directory + REGULAR_FILE);
        this.bold = read(directory + BOLD_FILE);
    }

    private static byte[] read(String path) {
        try (InputStream in = CvPdfFonts.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                log.error("Thieu file font trong classpath: {}", path);
                throw new IllegalStateException("Thieu file font trong classpath: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            log.error("Khong doc duoc file font: {}", path);
            throw new IllegalStateException("Khong doc duoc file font: " + path, e);
        }
    }

    // Nap hai font vao document, nhung tap con (embedSubset = true - a29).
    public Loaded load(PDDocument document) throws IOException {
        return new Loaded(
                PDType0Font.load(document, new ByteArrayInputStream(regular), true),
                PDType0Font.load(document, new ByteArrayInputStream(bold), true));
    }

    public record Loaded(PDType0Font regular, PDType0Font bold) {

        public PDType0Font of(boolean isBold) {
            return isBold ? bold : regular;
        }
    }
}
