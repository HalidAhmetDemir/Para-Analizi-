import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ParaAnalizi {

    private static final String VERI_DOSYASI = "finans_verileri.properties";
    private static final String LOG_DOSYASI = "islem_gecmisi.csv";
    private static final DateTimeFormatter GORUNUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final int MAAS_GUNU = 15;   // maaşın yattığı gün
    private static final int BURS_GUNU = 8;    // bursun yattığı gün
    private static final int EKSTRE_GUNU = 3;  // kredi kartı ekstresinin geldiği gün
    private static final int SON_ODEME_GUNU = 13; // kredi kartı son ödeme günü

    private static final Properties veriler = new Properties();
    private static final Scanner scanner = new Scanner(System.in).useLocale(Locale.of("tr", "TR"));

    public static void main(String[] args) {
        veriYukle();
        donguKontrolu();
        hatirlatmalariKontrolEt();

        boolean calisiyor = true;
        while (calisiyor) {
            System.out.println("\n--- 💰 PARA ANALİZİ MENÜSÜ ---");
            System.out.println("1 - Durum Raporunu Görüntüle");
            System.out.println("2 - Harcama Gir (son girişten bu yana)");
            System.out.println("3 - Gelir Gir (burs / diğer)");
            System.out.println("4 - Kredi Kartı İşlemleri");
            System.out.println("5 - Çıkış");
            System.out.print("Seçiminiz: ");

            String secim = scanner.next();
            switch (secim) {
                case "1" -> raporuYazdir();
                case "2" -> harcamaGir();
                case "3" -> gelirGir();
                case "4" -> kartMenusu();
                case "5" -> {
                    calisiyor = false;
                    System.out.println("Veriler kaydedildi. İyi günler!");
                }
                default -> System.out.println("Geçersiz seçim, lütfen tekrar deneyin.");
            }
        }
    }

    // ==================== VERİ YÜKLEME / KAYDETME ====================

    private static void veriYukle() {
        try (FileInputStream fis = new FileInputStream(VERI_DOSYASI)) {
            veriler.load(fis);
        } catch (FileNotFoundException e) {
            // ilk çalıştırma, sorun değil
        } catch (IOException e) {
            System.out.println("Veri yüklenirken hata oluştu!");
        }
    }

    private static void veriKaydet() {
        try (FileOutputStream fos = new FileOutputStream(VERI_DOSYASI)) {
            veriler.store(fos, "Para Analizi Verileri");
        } catch (IOException e) {
            System.out.println("Veriler kaydedilirken hata oluştu!");
        }
    }

    // Her gelir/gider/borç hareketini tarihiyle birlikte ayrı bir CSV dosyasına kaydeder.
    // Bu, ileride "geçmişe bakma" veya grafik çizme gibi işler istenirse hazır veri sağlar.
    private static void islemLogla(String tur, double tutar, String aciklama) {
        boolean yeniDosya = !Files.exists(Paths.get(LOG_DOSYASI));
        try (FileWriter fw = new FileWriter(LOG_DOSYASI, true);
             PrintWriter pw = new PrintWriter(fw)) {
            if (yeniDosya) {
                pw.println("tarih,tur,tutar,aciklama");
            }
            pw.printf("%s,%s,%.2f,%s%n", LocalDate.now(), tur, tutar, aciklama);
        } catch (IOException e) {
            System.out.println("İşlem geçmişe kaydedilirken hata oluştu.");
        }
    }

    // ==================== GÜVENLİ GİRİŞ YARDIMCILARI ====================

    private static double sayiOku(String mesaj) {
        while (true) {
            System.out.print(mesaj);
            try {
                double deger = scanner.nextDouble();
                return deger;
            } catch (InputMismatchException e) {
                System.out.println("❌ Geçersiz değer. Örnek: 1500,50");
                scanner.nextLine(); // hatalı girdiyi temizle
            }
        }
    }

    private static double bakiyeOku(String hesapAdi) {
        return Double.parseDouble(veriler.getProperty(hesapAdi, "0"));
    }

    private static String hesapSec() {
        System.out.println("Hangi hesap? 1-Ziraat  2-Papara  3-Nakit");
        while (true) {
            System.out.print("Seçim: ");
            String s = scanner.next();
            switch (s) {
                case "1": return "ziraat";
                case "2": return "papara";
                case "3": return "nakit";
                default: System.out.println("Geçersiz seçim, tekrar deneyin.");
            }
        }
    }

    // ==================== DÖNGÜ VE HATIRLATMALAR ====================

    private static LocalDate donguBaslangicHesapla(LocalDate tarih) {
        if (tarih.getDayOfMonth() >= MAAS_GUNU) {
            return LocalDate.of(tarih.getYear(), tarih.getMonth(), MAAS_GUNU);
        }
        return LocalDate.of(tarih.getYear(), tarih.getMonth(), MAAS_GUNU).minusMonths(1);
    }

    // Ayın 15'ine göre yeni maaş döngüsü başladıysa döngü verilerini sıfırlar ve maaşı ister.
    private static void donguKontrolu() {
        LocalDate bugun = LocalDate.now();
        LocalDate donguBaslangic = donguBaslangicHesapla(bugun);
        String kayitliDongu = veriler.getProperty("donguBaslangic", "");

        if (!kayitliDongu.equals(donguBaslangic.toString())) {
            System.out.println("\n🔔 YENİ MAAŞ DÖNGÜSÜ BAŞLADI! (" + donguBaslangic.format(GORUNUM_FORMAT) + ")");
            veriler.setProperty("donguBaslangic", donguBaslangic.toString());
            veriler.setProperty("donguGeliri", "0");
            veriKaydet();

            double maas = sayiOku("💰 Bu ayki maaş tutarınızı girin: ");
            gelirEkle("MAAS", maas, "Aylık maaş");
        }
    }

    // Uygulama her açıldığında bugünün tarihine göre unutulmuş olabilecek şeyleri hatırlatır.
    private static void hatirlatmalariKontrolEt() {
        LocalDate bugun = LocalDate.now();
        String buAy = YearMonth.from(bugun).toString();

        if (bugun.getDayOfMonth() >= BURS_GUNU && !"evet".equals(veriler.getProperty("bursGirildi_" + buAy))) {
            System.out.println("\n🎓 Burs günü (" + BURS_GUNU + "'i) geçti. Bu ay burs aldıysan girmeyi unutma (Menü 3).");
        }

        if (bugun.getDayOfMonth() >= EKSTRE_GUNU && !"evet".equals(veriler.getProperty("ekstreGirildi_" + buAy))) {
            System.out.println("\n💳 Kart ekstre günü (" + EKSTRE_GUNU + "'ü) geçti. Ekstreni girmeyi unutma (Menü 4).");
        }

        double kartBorcu = bakiyeOku("kartBorcu");
        if (kartBorcu > 0) {
            if (bugun.getDayOfMonth() <= SON_ODEME_GUNU) {
                long kalanGun = SON_ODEME_GUNU - bugun.getDayOfMonth();
                System.out.printf("%n⚠️ Kredi kartı borcun var: %.2f TL. Son ödeme gününe %d gün kaldı.%n", kartBorcu, kalanGun);
            } else {
                System.out.printf("%n🚨 DİKKAT: Kart son ödeme günü (%d'ü) geçti ve %.2f TL ödenmemiş borcun var!%n", SON_ODEME_GUNU, kartBorcu);
            }
        }
    }

    // ==================== GELİR ====================

    private static void gelirGir() {
        System.out.println("Gelir türü: 1-Burs  2-Diğer (hediye, ek gelir vb.)");
        System.out.print("Seçim: ");
        String secim = scanner.next();
        double tutar = sayiOku("Tutar: ");

        if ("1".equals(secim)) {
            gelirEkle("BURS", tutar, "Burs");
            veriler.setProperty("bursGirildi_" + YearMonth.from(LocalDate.now()).toString(), "evet");
            veriKaydet();
        } else {
            gelirEkle("DIGER", tutar, "Diğer gelir");
        }
    }

    private static void gelirEkle(String tur, double tutar, String aciklama) {
        String hesap = hesapSec();
        double mevcut = bakiyeOku(hesap);
        veriler.setProperty(hesap, String.valueOf(mevcut + tutar));

        double donguGeliri = bakiyeOku("donguGeliri");
        veriler.setProperty("donguGeliri", String.valueOf(donguGeliri + tutar));

        veriKaydet();
        islemLogla("GELIR_" + tur, tutar, aciklama + " -> " + hesap);
        System.out.println("✅ Gelir kaydedildi.");
    }

    // ==================== HARCAMA ====================

    // Kullanıcı her gün girmek zorunda değil: en son ne zaman harcama girdiyse
    // (veya döngü başlangıcından beri) o tarihten bugüne kadar TOPLAM ne harcandığını sorar.
    private static void harcamaGir() {
        LocalDate bugun = LocalDate.now();
        String sonTarihStr = veriler.getProperty("sonHarcamaTarihi", veriler.getProperty("donguBaslangic", bugun.toString()));
        LocalDate sonTarih = LocalDate.parse(sonTarihStr);
        long gunSayisi = ChronoUnit.DAYS.between(sonTarih, bugun);

        System.out.println("Son giriş: " + sonTarih.format(GORUNUM_FORMAT) + " (" + gunSayisi + " gün önce)");
        double tutar = sayiOku("Bu aralıkta toplam ne kadar harcadın? ");
        String hesap = hesapSec();

        double mevcut = bakiyeOku(hesap);
        veriler.setProperty(hesap, String.valueOf(mevcut - tutar));
        veriler.setProperty("sonHarcamaTarihi", bugun.toString());
        veriKaydet();

        islemLogla("HARCAMA", tutar, gunSayisi + " gunluk harcama -> " + hesap);
        System.out.println("✅ Harcama kaydedildi.");
    }

    // ==================== KREDİ KARTI ====================

    private static void kartMenusu() {
        System.out.println("\n--- 💳 KREDİ KARTI ---");
        System.out.println("1 - Ekstre Gir (yeni borç)");
        System.out.println("2 - Ödeme Yap");
        System.out.print("Seçim: ");
        String secim = scanner.next();

        if ("1".equals(secim)) {
            double tutar = sayiOku("Ekstre tutarı: ");
            double kartBorcu = bakiyeOku("kartBorcu");
            veriler.setProperty("kartBorcu", String.valueOf(kartBorcu + tutar));
            veriler.setProperty("ekstreGirildi_" + YearMonth.from(LocalDate.now()).toString(), "evet");
            veriKaydet();
            islemLogla("KART_EKSTRE", tutar, "Yeni ekstre");
            System.out.println("✅ Ekstre kaydedildi. Son ödeme günü: ayın " + SON_ODEME_GUNU + "'ü.");
        } else if ("2".equals(secim)) {
            double kartBorcu = bakiyeOku("kartBorcu");
            if (kartBorcu <= 0) {
                System.out.println("Ödenecek borç yok.");
                return;
            }
            System.out.printf("Mevcut borç: %.2f TL%n", kartBorcu);
            double tutar = sayiOku("Ödeme tutarı: ");
            String hesap = hesapSec();

            double mevcut = bakiyeOku(hesap);
            veriler.setProperty(hesap, String.valueOf(mevcut - tutar));
            veriler.setProperty("kartBorcu", String.valueOf(Math.max(0, kartBorcu - tutar)));
            veriKaydet();
            islemLogla("KART_ODEME", tutar, "Kart ödemesi -> " + hesap);
            System.out.println("✅ Ödeme kaydedildi.");
        } else {
            System.out.println("Geçersiz seçim.");
        }
    }

    // ==================== RAPOR ====================

    private static void raporuYazdir() {
        if (!veriler.containsKey("donguBaslangic")) {
            System.out.println("Henüz veri yok.");
            return;
        }

        double ziraat = bakiyeOku("ziraat");
        double papara = bakiyeOku("papara");
        double nakit = bakiyeOku("nakit");
        double toplamBakiye = ziraat + papara + nakit;

        double kartBorcu = bakiyeOku("kartBorcu");
        double netDurum = toplamBakiye - kartBorcu;

        double donguGeliri = bakiyeOku("donguGeliri");

        LocalDate bugun = LocalDate.now();
        LocalDate donguBaslangic = LocalDate.parse(veriler.getProperty("donguBaslangic"));
        LocalDate donguBitis = donguBaslangic.plusMonths(1);

        long gecenGun = ChronoUnit.DAYS.between(donguBaslangic, bugun);
        long kalanGun = ChronoUnit.DAYS.between(bugun, donguBitis);
        long toplamDonguGunu = ChronoUnit.DAYS.between(donguBaslangic, donguBitis);

        double idealGunlukButce = toplamDonguGunu > 0 ? donguGeliri / toplamDonguGunu : 0;
        double olmasiGerekenNetDurum = donguGeliri - (idealGunlukButce * gecenGun);
        double fark = netDurum - olmasiGerekenNetDurum;

        System.out.println("\n📊 --- ANALİZ RAPORU ---");
        System.out.println("📅 Tarih : " + bugun.format(GORUNUM_FORMAT));
        System.out.println("🔄 Dönem : " + donguBaslangic.format(GORUNUM_FORMAT) + " - " + donguBitis.format(GORUNUM_FORMAT));
        System.out.println("-------------------------------------");
        System.out.println("⏳ Geçen Süre  : " + gecenGun + " gün");
        System.out.println("🔮 Kalan Süre  : " + kalanGun + " gün");
        System.out.println("-------------------------------------");
        System.out.printf("💰 Bu dönem toplam gelir : %.2f TL%n", donguGeliri);
        System.out.printf("💳 Ziraat  : %.2f TL%n", ziraat);
        System.out.printf("💳 Papara  : %.2f TL%n", papara);
        System.out.printf("💵 Nakit   : %.2f TL%n", nakit);
        System.out.printf("💸 Toplam Bakiye : %.2f TL%n", toplamBakiye);
        if (kartBorcu > 0) {
            System.out.printf("🔴 Ödenmemiş Kart Borcu : -%.2f TL%n", kartBorcu);
        }
        System.out.printf("📌 NET DURUM (borç dahil) : %.2f TL%n", netDurum);
        System.out.println("-------------------------------------");
        System.out.printf("🎯 Günlük İdeal Bütçe : %.2f TL%n", idealGunlukButce);

        if (fark >= 0) {
            System.out.printf("✅ DURUM İYİ: İdeal planın %.2f TL önündesin.%n", fark);
        } else {
            System.out.printf("⚠️ DİKKAT: İdeal planın %.2f TL gerisindesin.%n", Math.abs(fark));
        }
    }
}
