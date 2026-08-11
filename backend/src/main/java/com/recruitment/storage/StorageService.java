package com.recruitment.storage;

import java.io.InputStream;

public interface StorageService {

    /**
     * Luu noi dung vao {subdirectory}/{filename}, tra ve public URL (vd "/uploads/logos/xxx.png").
     */
    String store(String subdirectory, String filename, InputStream content);

    /**
     * Xoa file tuong ung voi public URL da tra ve tu store(). Khong lam gi neu file khong ton tai.
     */
    void delete(String publicUrl);
}
