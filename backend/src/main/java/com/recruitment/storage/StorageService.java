package com.recruitment.storage;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.core.io.Resource;

public interface StorageService {

    /**
     * Luu noi dung vao {subdirectory}/{filename}, tra ve public URL (vd "/uploads/logos/xxx.png").
     */
    String store(String subdirectory, String filename, InputStream content);

    /**
     * Xoa file tuong ung voi public URL da tra ve tu store(). Khong lam gi neu file khong ton tai.
     */
    void delete(String publicUrl);

    /**
     * Doc lai file da luu qua store(), dua tren key noi bo dang "{subdirectory}/{filename}" -
     * KHAC voi publicUrl dung boi store()/delete() (khong co tien to "/uploads/"). Dung cho file
     * can kiem quyen truoc khi doc (vd resume), khac voi file public phuc vu qua static resource
     * handler (vd logo). Rong neu key khong hop le hoac file khong ton tai.
     */
    Optional<Resource> load(String key);
}
