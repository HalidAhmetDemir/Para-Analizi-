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
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final int MAAS_GUNU     = 15;
    private static final int BURS_GUNU     = 8;
    private static final int EKSTRE_GUNU   = 3;
    private static final int SON_ODEME_GUNU = 13;

    private static final Properties veriler = new Properties();
    private static final Scanner scanner = new Scanner(System.in).useLocale(Locale.of("tr", "TR"));

    // ==================== MAIN ====================

    public static void main(String[] args) {
        veriYukle();
        ilkKurulumKontrol();   // hiç veri yoksa sıfırdan kurulum
        donguKontrolu();       // yeni döngü başladıysa maaş sor
        hatirlatmalariGoster();

        boolean calisiyor = true;
        while (calisiyor) {
            System.out.println("\n--- 💰 PARA ANALİZİ MENÜSÜ ---");
            System.out.println("1 - Durum Raporunu Görüntüle");
            System.out.println("2 - Harcama Gir");
            System.out.println("3 - Gelir Gir (burs / diğer)");
            System.out.println("4 - Kredi Kartı İşlemleri");
            System.out.println("5 - Bakiyeleri Manuel Düzelt");
            System.out.println("6 - Çıkış");
            System.out.print("Seçiminiz: ");

            String secim = scanner.next();
            switch (secim) {
                case "1" -> raporuYazdir();
                case "2" -> harcamaGir();
                case "3" -> gelirGir();
                case "4" -> kartMenusu();
                case "5" -> bakiyeleriManuelDuzelt();
                case "6" -> {
                    calisiyor = false;
                    System.out.println("Veriler kaydedildi. İyi günler!");
                }
                default -> System.out.println("Geçersiz seçim.");
            }
        }
    }

    // ==================== VERİ YÜKLEME / KAYDETME ====================

    private static void veriYukle() {
        try (FileInputStream fis = new FileInputStream(VERI_DOSYASI)) {
            veriler.load(fis);
        } catch (FileNotFoundException e) {
            // ilk çalıştırma
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

    private static void islemLogla(String tur, double tutar, String aciklama) {
        boolean yeniDosya = !Files.exists(Paths.get(LOG_DOSYASI));
        try (FileWriter fw = new FileWriter(LOG_DOSYASI, true);
             PrintWriter pw = new PrintWriter(fw)) {
            if (yeniDosya) pw.println("tarih,tur,tutar,aciklama");
            pw.printf("%s,%s,%.2f,%s%n", LocalDate.now(), tur, tutar, aciklama);
        } catch (IOException e) {
            System.out.println("İşlem geçmişe kaydedilemedi.");
        }
    }

    // ==================== GİRİŞ YARDIMCILARI ====================

    private static double sayiOku(String mesaj) {
        while (true) {
            System.out.print(mesaj);
            try {
                double d = scanner.nextDouble();
                if (d < 0) { System.out.println("❌ Negatif değer girilemez."); continue; }
                return d;
            } catch (InputMismatchException e) {
                System.out.println("❌ Geçersiz değer. Virgüllü sayı için örnek: 1500,50");
                scanner.nextLine();
            }
        }
    }

    private static double bakiyeOku(String key) {
        try { return Double.parseDouble(veriler.getProperty(key, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String hesapSec() {
        double z = bakiyeOku("ziraat"), p = bakiyeOku("papara"), n = bakiyeOku("nakit");
        System.out.printf("Hangi hesap?  1-Ziraat (%.2f TL)  2-Papara (%.2f TL)  3-Nakit (%.2f TL)%n", z, p, n);
        while (true) {
            System.out.print("Seçim: ");
            switch (scanner.next()) {
                case "1": return "ziraat";
                case "2": return "papara";
                case "3": return "nakit";
                default: System.out.println("Geçersiz seçim.");
            }
        }
    }

    private static boolean evetHayirSor(String soru) {
        while (true) {
            System.out.print(soru + " (e/h): ");
            String cevap = scanner.next().toLowerCase(Locale.of("tr", "TR"));
            if (cevap.equals("e")) return true;
            if (cevap.equals("h")) return false;
            System.out.println("Lütfen 'e' veya 'h' girin.");
        }
    }

    // ==================== İLK KURULUM ====================

    // Program hiç çalıştırılmamışsa mevcut bakiyeleri ve açık borcu sorar.
    // Bu sayede sisteme geçişte eldeki gerçek para doğru yansır.
    private static void ilkKurulumKontrol() {
        if (veriler.containsKey("kurulumTamamlandi")) return;

        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║  PARA ANALİZİ - İLK KURULUM     ║");
        System.out.println("╚══════════════════════════════════╝");
        System.out.println("Program ilk kez çalıştırılıyor. Mevcut bakiyelerini girelim.\n");

        double z = sayiOku("Ziraat bakiyeniz: ");
        double p = sayiOku("Papara bakiyeniz: ");
        double n = sayiOku("Nakit paranız: ");
        veriler.setProperty("ziraat", String.valueOf(z));
        veriler.setProperty("papara", String.valueOf(p));
        veriler.setProperty("nakit", String.valueOf(n));

        // Açık kart borcu varsa hemen kaydet
        if (evetHayirSor("Şu an ödenmemiş kredi kartı borcun var mı?")) {
            double borc = sayiOku("Mevcut kart borcu: ");
            veriler.setProperty("kartBorcu", String.valueOf(borc));
        } else {
            veriler.setProperty("kartBorcu", "0");
        }

        veriler.setProperty("kurulumTamamlandi", "evet");
        veriler.setProperty("donguGeliri", "0");
        veriKaydet();
        System.out.println("✅ Kurulum tamamlandı.\n");
    }

    // ==================== DÖNGÜ KONTROLÜ ====================

    private static LocalDate donguBaslangicHesapla(LocalDate tarih) {
        if (tarih.getDayOfMonth() >= MAAS_GUNU)
            return LocalDate.of(tarih.getYear(), tarih.getMonth(), MAAS_GUNU);
        return LocalDate.of(tarih.getYear(), tarih.getMonth(), MAAS_GUNU).minusMonths(1);
    }

    // Yeni döngü tespitinde:
    // 1) donguGeliri sıfırlanır (dönemin geliri sıfırdan sayılır)
    // 2) Mevcut bakiyeler sıfırlanmaz — gerçek para orada duruyor
    // 3) Maaş sorulur, ilgili hesaba eklenir
    private static void donguKontrolu() {
        LocalDate bugun = LocalDate.now();
        LocalDate donguBaslangic = donguBaslangicHesapla(bugun);
        String kayitli = veriler.getProperty("donguBaslangic", "");

        if (kayitli.equals(donguBaslangic.toString())) return; // aynı dönem, bir şey yapma

        System.out.println("\n🔔 YENİ MAAŞ DÖNGÜSÜ BAŞLADI! (" + donguBaslangic.format(FMT) + ")");

        // Önceki dönemden kalan para sorunsuzca devam eder, sadece döngü sayacı sıfırlanır
        veriler.setProperty("donguBaslangic", donguBaslangic.toString());
        veriler.setProperty("donguGeliri", "0");
        veriler.setProperty("sonHarcamaTarihi", donguBaslangic.toString());
        veriKaydet();

        double maas = sayiOku("💰 Bu ayki maaş tutarını girin: ");
        String hesap = hesapSec();
        double mevcut = bakiyeOku(hesap);
        veriler.setProperty(hesap, String.valueOf(mevcut + maas));
        veriler.setProperty("donguGeliri", String.valueOf(maas));
        veriKaydet();
        islemLogla("GELIR_MAAS", maas, "Aylık maaş -> " + hesap);
        System.out.println("✅ Maaş kaydedildi.");
    }

    // ==================== HATIRLATMALAR ====================

    private static void hatirlatmalariGoster() {
        LocalDate bugun = LocalDate.now();
        String buAy = YearMonth.from(bugun).toString();
        int gun = bugun.getDayOfMonth();

        // Burs hatırlatması
        if (gun >= BURS_GUNU && !"evet".equals(veriler.getProperty("bursGirildi_" + buAy))) {
            System.out.println("\n🎓 Burs günü (" + BURS_GUNU + "'i) geçti. Bu ay burs aldıysan Menü 3'ten gir.");
        }

        // Ekstre hatırlatması
        if (gun >= EKSTRE_GUNU && !"evet".equals(veriler.getProperty("ekstreGirildi_" + buAy))) {
            System.out.println("💳 Ekstre günü (" + EKSTRE_GUNU + "'ü) geçti. Ekstreni Menü 4'ten girmeyi unutma.");
        }

        // Kart borcu + son ödeme uyarısı
        double kartBorcu = bakiyeOku("kartBorcu");
        if (kartBorcu > 0) {
            if (gun <= SON_ODEME_GUNU) {
                long kalan = SON_ODEME_GUNU - gun;
                System.out.printf("⚠️  Kart borcun: %.2f TL — Son ödeme gününe %d gün kaldı (ayın %d'ü).%n",
                        kartBorcu, kalan, SON_ODEME_GUNU);
            } else {
                System.out.printf("🚨 ÖDEME GECİKTİ! %.2f TL kart borcun var, son ödeme günü (ayın %d'ü) geçti!%n",
                        kartBorcu, SON_ODEME_GUNU);
            }
        }
    }

    // ==================== GELİR ====================

    private static void gelirGir() {
        System.out.println("Gelir türü:  1-Burs  2-Diğer (hediye, ek gelir vb.)");
        System.out.print("Seçim: ");
        String secim = scanner.next();
        double tutar = sayiOku("Tutar: ");
        String hesap = hesapSec();

        double mevcut = bakiyeOku(hesap);
        veriler.setProperty(hesap, String.valueOf(mevcut + tutar));
        double donguGeliri = bakiyeOku("donguGeliri");
        veriler.setProperty("donguGeliri", String.valueOf(donguGeliri + tutar));

        if ("1".equals(secim)) {
            veriler.setProperty("bursGirildi_" + YearMonth.from(LocalDate.now()), "evet");
            islemLogla("GELIR_BURS", tutar, "Burs -> " + hesap);
        } else {
            islemLogla("GELIR_DIGER", tutar, "Diğer gelir -> " + hesap);
        }

        veriKaydet();
        System.out.println("✅ Gelir kaydedildi.");
    }

    // ==================== HARCAMA ====================

    // Kaç gün önce son giriş yapıldığını gösterir, o aralığın toplam harcamasını sorar.
    // Birden fazla hesaptan para çıkmışsa her hesabı ayrı ayrı gir diye sorar.
    private static void harcamaGir() {
        LocalDate bugun = LocalDate.now();
        String sonTarihStr = veriler.getProperty("sonHarcamaTarihi",
                veriler.getProperty("donguBaslangic", bugun.toString()));
        LocalDate sonTarih = LocalDate.parse(sonTarihStr);
        long gunSayisi = ChronoUnit.DAYS.between(sonTarih, bugun);

        System.out.println("Son harcama girişi: " + sonTarih.format(FMT) + " (" + gunSayisi + " gün önce)");

        boolean devam = true;
        while (devam) {
            double tutar = sayiOku("Harcama tutarı (hangi hesaptan çıktıysa onu seçeceğiz): ");
            String hesap = hesapSec();

            double mevcut = bakiyeOku(hesap);
            if (tutar > mevcut) {
                System.out.printf("⚠️  %s bakiyeniz %.2f TL. Bu hesaptan bu kadar çıkış yapılırsa bakiye eksi olur.%n",
                        hesap, mevcut);
                if (!evetHayirSor("Yine de devam etmek istiyor musun?")) {
                    System.out.println("İptal edildi.");
                    return;
                }
            }

            veriler.setProperty(hesap, String.valueOf(mevcut - tutar));
            islemLogla("HARCAMA", tutar, gunSayisi + " gunluk harcama -> " + hesap);
            System.out.println("✅ Kaydedildi.");

            devam = evetHayirSor("Bu aralıkta başka bir hesaptan da harcama var mı?");
        }

        veriler.setProperty("sonHarcamaTarihi", bugun.toString());
        veriKaydet();
    }

    // ==================== KREDİ KARTI ====================

    private static void kartMenusu() {
        System.out.println("\n--- 💳 KREDİ KARTI ---");
        System.out.println("1 - Ekstre Gir");
        System.out.println("2 - Ödeme Yap");
        System.out.print("Seçim: ");
        String secim = scanner.next();

        if ("1".equals(secim)) {
            double tutar = sayiOku("Ekstre tutarı: ");
            double mevcut = bakiyeOku("kartBorcu");
            veriler.setProperty("kartBorcu", String.valueOf(mevcut + tutar));
            veriler.setProperty("ekstreGirildi_" + YearMonth.from(LocalDate.now()), "evet");
            veriKaydet();
            islemLogla("KART_EKSTRE", tutar, "Yeni ekstre");
            System.out.printf("✅ Ekstre kaydedildi. Toplam kart borcun: %.2f TL. Son ödeme: ayın %d'ü.%n",
                    bakiyeOku("kartBorcu"), SON_ODEME_GUNU);

        } else if ("2".equals(secim)) {
            double kartBorcu = bakiyeOku("kartBorcu");
            if (kartBorcu <= 0) { System.out.println("Ödenecek borç yok."); return; }

            System.out.printf("Mevcut kart borcu: %.2f TL%n", kartBorcu);
            double tutar = sayiOku("Ödeme tutarı: ");
            String hesap = hesapSec();

            double mevcut = bakiyeOku(hesap);
            if (tutar > mevcut) {
                System.out.printf("⚠️  %s bakiyeniz %.2f TL, ödeme tutarından az.%n", hesap, mevcut);
                if (!evetHayirSor("Yine de devam?")) { System.out.println("İptal."); return; }
            }

            veriler.setProperty(hesap, String.valueOf(mevcut - tutar));
            veriler.setProperty("kartBorcu", String.valueOf(Math.max(0, kartBorcu - tutar)));
            veriKaydet();
            islemLogla("KART_ODEME", tutar, "Kart ödemesi -> " + hesap);
            System.out.printf("✅ Ödeme kaydedildi. Kalan borç: %.2f TL%n", bakiyeOku("kartBorcu"));

        } else {
            System.out.println("Geçersiz seçim.");
        }
    }

    // ==================== MANUEL DÜZELTME ====================

    // Bankadan farklı bir tutar görüyorsan gerçek bakiyeyi buradan düzelt.
    // Fark log'a "DUZELTME" olarak işlenir.
    private static void bakiyeleriManuelDuzelt() {
        System.out.println("\n⚙️  BAKİYE MANUEL DÜZELTME");
        System.out.println("Gerçek bakiyeni gir. Sistemdeki değerle fark log'a kaydedilir.");

        String[] hesaplar = {"ziraat", "papara", "nakit"};
        String[] isimler  = {"Ziraat", "Papara", "Nakit"};

        for (int i = 0; i < hesaplar.length; i++) {
            double eski = bakiyeOku(hesaplar[i]);
            System.out.printf("%s (mevcut: %.2f TL) — değiştirmek istiyor musun?%n", isimler[i], eski);
            if (evetHayirSor("")) {
                double yeni = sayiOku("Gerçek bakiye: ");
                double fark = yeni - eski;
                veriler.setProperty(hesaplar[i], String.valueOf(yeni));
                islemLogla("DUZELTME_" + hesaplar[i].toUpperCase(), fark,
                        String.format("%.2f -> %.2f", eski, yeni));
                System.out.println("✅ Güncellendi.");
            }
        }
        veriKaydet();
    }

    // ==================== RAPOR ====================

    private static void raporuYazdir() {
        if (!veriler.containsKey("donguBaslangic")) {
            System.out.println("Henüz döngü verisi yok.");
            return;
        }

        double ziraat  = bakiyeOku("ziraat");
        double papara  = bakiyeOku("papara");
        double nakit   = bakiyeOku("nakit");
        double toplam  = ziraat + papara + nakit;
        double kartBorcu = bakiyeOku("kartBorcu");
        double net     = toplam - kartBorcu;
        double donguGeliri = bakiyeOku("donguGeliri");

        LocalDate bugun        = LocalDate.now();
        LocalDate donguBaslangic = LocalDate.parse(veriler.getProperty("donguBaslangic"));
        LocalDate donguBitis   = donguBaslangic.plusMonths(1);

        long gecenGun  = ChronoUnit.DAYS.between(donguBaslangic, bugun);
        long kalanGun  = ChronoUnit.DAYS.between(bugun, donguBitis);
        long toplamGun = ChronoUnit.DAYS.between(donguBaslangic, donguBitis);

        double idealGunluk = toplamGun > 0 ? donguGeliri / toplamGun : 0;
        // Olmasi gereken net durum: donemin basindaki gelir eksi o gune kadar harcamasi gereken miktar
        double olmasiGereken = donguGeliri - (idealGunluk * gecenGun);
        double fark = net - olmasiGereken;

        System.out.println("\n📊 ─── ANALİZ RAPORU ───");
        System.out.println("📅 Tarih : " + bugun.format(FMT));
        System.out.println("🔄 Dönem : " + donguBaslangic.format(FMT) + " → " + donguBitis.format(FMT));
        System.out.println("─────────────────────────────────────");
        System.out.println("⏳ Geçen  : " + gecenGun + " gün   |   🔮 Kalan : " + kalanGun + " gün");
        System.out.println("─────────────────────────────────────");
        System.out.printf("💰 Bu dönem toplam gelir : %.2f TL%n", donguGeliri);
        System.out.println("─────────────────────────────────────");
        System.out.printf("🏦 Ziraat  : %.2f TL%n", ziraat);
        System.out.printf("📱 Papara  : %.2f TL%n", papara);
        System.out.printf("💵 Nakit   : %.2f TL%n", nakit);
        System.out.printf("💸 Toplam Bakiye  : %.2f TL%n", toplam);
        if (kartBorcu > 0)
            System.out.printf("🔴 Kart Borcu     : -%.2f TL%n", kartBorcu);
        System.out.printf("📌 NET DURUM      : %.2f TL%n", net);
        System.out.println("─────────────────────────────────────");
        System.out.printf("🎯 Günlük İdeal Bütçe : %.2f TL%n", idealGunluk);

        if (gecenGun == 0) {
            System.out.println("(Döngü bugün başladı, karşılaştırma için veri yok.)");
        } else if (fark >= 0) {
            System.out.printf("✅ İdeal planın %.2f TL önündesin.%n", fark);
        } else {
            System.out.printf("⚠️  İdeal planın %.2f TL gerisindesin — harcamaları kıs.%n", Math.abs(fark));
        }
    }
}
