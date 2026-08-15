import java.io.*;
import java.util.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

public class ParaAnalizi {

    private static final String DOSYA_YOLU = "finans_verileri.properties";
    private static Properties veriler = new Properties();
    private static Scanner scanner = new Scanner(System.in).useLocale(Locale.of("tr", "TR"));
    private static final int MAAS_GUNU = 15;

    public static void main(String[] args) {
        veriYukle();
        aylikDonguKontrolu();

        boolean calisiyor = true;
        while (calisiyor) {
            System.out.println("\n--- 💰 PARA ANALİZİ MENÜSÜ ---");
            System.out.println("1 - Mevcut Durumu ve Raporu Görüntüle");
            System.out.println("2 - Bakiyeleri Güncelle (Harcama Sonrası)");
            System.out.println("3 - Çıkış");
            System.out.print("Seçiminiz: ");

            String secim = scanner.next();
            // deneme 123
            switch (secim) {
                case "1":
                    raporuYazdir();
                    break;
                case "2":
                    bakiyeleriGuncelle();
                    break;
                case "3":
                    calisiyor = false;
                    System.out.println("Veriler kaydedildi. İyi günler!");
                    break;
                default:
                    System.out.println("Geçersiz seçim, lütfen tekrar deneyin.");
            }
        }
    }

    private static void veriYukle() {
        try (FileInputStream fis = new FileInputStream(DOSYA_YOLU)) {
            veriler.load(fis);
        } catch (FileNotFoundException e) {
            // Dosya henüz yoksa sistem boş başlar, hata fırlatmaya gerek yok.
        } catch (IOException e) {
            System.out.println("Veri yüklenirken hata oluştu!");
        }
    }

    private static void veriKaydet() {
        try (FileOutputStream fos = new FileOutputStream(DOSYA_YOLU)) {
            veriler.store(fos, "Para Analizi Verileri");
        } catch (IOException e) {
            System.out.println("Veriler kaydedilirken hata oluştu!");
        }
    }

    private static void aylikDonguKontrolu() {
        LocalDate bugun = LocalDate.now();
        LocalDate donguBaslangic;

        if (bugun.getDayOfMonth() >= MAAS_GUNU) {
            donguBaslangic = LocalDate.of(bugun.getYear(), bugun.getMonth(), MAAS_GUNU);
        } else {
            donguBaslangic = LocalDate.of(bugun.getYear(), bugun.getMonth(), MAAS_GUNU).minusMonths(1);
        }

        String kayitliDongu = veriler.getProperty("donguBaslangic", "");

        if (!kayitliDongu.equals(donguBaslangic.toString())) {
            System.out.println("\n🔔 YENİ MAAŞ DÖNGÜSÜ TESPİT EDİLDİ! (" + donguBaslangic + ")");
            veriler.setProperty("donguBaslangic", donguBaslangic.toString());

            try {
                System.out.print("💰 Bu ayki maaş tutarınızı girin: ");
                double maas = scanner.nextDouble();
                veriler.setProperty("maas", String.valueOf(maas));

                System.out.println("\nLütfen güncel bakiyelerinizi de girerek döngüyü başlatın:");
                bakiyeleriGuncelle();
            } catch (InputMismatchException e) {
                System.out.println("Hatalı giriş! Uygulama kapatılıyor.");
                System.exit(0);
            }
        }
    }

    private static void bakiyeleriGuncelle() {
        try {
            System.out.print("💳 Ziraat Bakiyeniz: ");
            veriler.setProperty("ziraat", String.valueOf(scanner.nextDouble()));

            System.out.print("💳 Papara Bakiyeniz: ");
            veriler.setProperty("papara", String.valueOf(scanner.nextDouble()));

            System.out.print("💵 Nakit Paranız: ");
            veriler.setProperty("nakit", String.valueOf(scanner.nextDouble()));

            veriKaydet();
            System.out.println("✅ Bakiyeler hafızaya başarıyla kaydedildi!");
        } catch (InputMismatchException e) {
            System.out.println("❌ HATA: Lütfen sayısal değer giriniz (Örn: 1500,50).");
            scanner.nextLine(); // Hatalı girdiyi temizler
        }
    }

    private static void raporuYazdir() {
        if (!veriler.containsKey("maas") || !veriler.containsKey("ziraat")) {
            System.out.println("Görüntülenecek yeterli veri yok. Lütfen bakiyeleri güncelleyin.");
            return;
        }

        double ziraat = Double.parseDouble(veriler.getProperty("ziraat", "0"));
        double papara = Double.parseDouble(veriler.getProperty("papara", "0"));
        double nakit = Double.parseDouble(veriler.getProperty("nakit", "0"));
        double maasMiktari = Double.parseDouble(veriler.getProperty("maas", "0"));

        double suAnkiPara = ziraat + papara + nakit;
        double harcananPara = maasMiktari - suAnkiPara;

        LocalDate bugun = LocalDate.now();
        LocalDate donguBaslangic = LocalDate.parse(veriler.getProperty("donguBaslangic"));
        LocalDate donguBitis = donguBaslangic.plusMonths(1);

        long gecenGun = ChronoUnit.DAYS.between(donguBaslangic, bugun);
        long kalanGun = ChronoUnit.DAYS.between(bugun, donguBitis);
        long toplamDonguGunu = ChronoUnit.DAYS.between(donguBaslangic, donguBitis);

        double gunlukOrtalamaHarcama = gecenGun > 0 ? harcananPara / gecenGun : 0;
        double gunlukLimit = kalanGun > 0 ? suAnkiPara / kalanGun : suAnkiPara;

        double idealGunlukHarcama = maasMiktari / toplamDonguGunu;
        double olmasiGerekenBakiye = maasMiktari - (idealGunlukHarcama * gecenGun);
        double fark = suAnkiPara - olmasiGerekenBakiye;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM");

        System.out.println("\n📊 --- ANALİZ RAPORU ---");
        System.out.println("📅 Tarih: " + bugun.format(fmt));
        System.out.println("🔄 Dönem: " + donguBaslangic.format(fmt) + " - " + donguBitis.format(fmt));
        System.out.println("-------------------------------------");
        System.out.println("⏳ Geçen Süre   : " + gecenGun + " gün");
        System.out.println("🔮 Kalan Süre   : " + kalanGun + " gün");
        System.out.println("-------------------------------------");
        System.out.printf("💸 Toplam Mevcut : %.2f TL\n", suAnkiPara);
        System.out.printf("📉 Harcanan      : %.2f TL\n", harcananPara);
        System.out.printf("🛒 Günlük Harcama: %.2f TL (Ortalama)\n", gunlukOrtalamaHarcama);
        System.out.printf("🎯 Günlük Limit  : %.2f TL (Kalan günler için)\n", gunlukLimit);
        System.out.println("-------------------------------------");

        if (fark >= 0) {
            System.out.printf("✅ DURUM İYİ: İdeal planın %.2f TL önündesin.\n", fark);
        } else {
            System.out.printf("⚠️ DİKKAT: İdeal planın %.2f TL gerisindesin. Harcamaları kıs.\n", Math.abs(fark));
        }
    }
}