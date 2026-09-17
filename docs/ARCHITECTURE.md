# DraBornPortal v0.1 — Mimari

## 1. Neden Native Android?

DraBornPortal'ın ana işi cihaz içi OCR, cihaz içi çeviri, güvenli Sony oturumu saklama ve ileride arka planda capture kontrolüdür. Bu nedenle v0.1 Kotlin + Jetpack Compose ile native Android olarak başlar.

## 2. Ücretsiz çeviri zinciri

1. Capture görüntüsü telefona gelir.
2. Bundled ML Kit Latin Text Recognition görüntüdeki İngilizce metni cihazda okur.
3. OCR metni normalize edilir; ardışık tekrarlar temizlenir.
4. Oyun/ürün özel isimleri `GameGlossary` tarafından geçici tokenlarla korunur.
5. ML Kit EN→TR modeli metni cihazda Türkçeye çevirir.
6. Korunan özel isimler geri yüklenir.
7. Kaynak + Türkçe sonuç yerel geçmişe yazılır.
8. Aynı kaynak metin yeniden gelirse geçmişte çoğaltılmaz.

Bu zincirde kullanım başına ücretli çeviri servisi yoktur. Çeviri dil modeli ilk kullanımda indirilmelidir.

## 3. Sony capture zinciri

Hedef:

Portal Create → Sony Cloud Gallery → yeni capture ID → görüntüyü telefon belleğine al → TranslationEngine → Türkçe

Sony Cloud Gallery üçüncü taraflar için belgelenmiş kararlı bir genel API değildir. Bu nedenle `CloudGalleryProvider` ayrı bir sınırdır. Sony değişiklik yaptığında yalnız bu modül değiştirilir.

## 4. v0.1 doğrulama sırası

- [x] Repo ve Android mimarisi
- [x] Bundled cihaz-içi OCR bağımlılığı
- [x] Cihaz-içi EN→TR çeviri bağımlılığı
- [x] Oyun terimi koruma katmanı
- [x] Yerel çeviri geçmişi
- [x] Android Photo Picker ile screenshot testi
- [x] GitHub Actions debug APK derleme
- [ ] Gerçek Android cihazda OCR/çeviri kalite testi
- [ ] Portal Create → Cloud'a düşme gecikmesini ölç
- [ ] Güvenli Sony oturum akışını ekle
- [ ] En yeni cloud capture'ı otomatik algıla
- [ ] Capture ID tekrar filtresi + kontrollü polling/backoff
- [ ] Capture geldiği anda otomatik çeviri

## 5. Güvenlik kuralları

- PSN parolası kaynak koda yazılmaz.
- Access/refresh token GitHub'a commit edilmez.
- Token loglanmaz.
- Sony oturum verisi Android Keystore destekli şifreli depoda tutulur.
- HTTPS dışında Sony/media iletişimine izin verilmez.

## 6. Gerçekçilik sınırı

Bu sistem video karelerini gerçek zamanlı olarak Portal'dan kopyalamaz. Kullanıcının Portal'da aldığı screenshot'ın Sony Cloud'a ulaşmasından sonra otomatik çeviri hedeflenir. Toplam gecikmenin en büyük ve bizim kontrolümüz dışındaki bölümü Portal → Sony Cloud upload süresidir; gerçek cihaz testiyle ölçülmelidir.
