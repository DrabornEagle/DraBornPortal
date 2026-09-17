# DraBornPortal

DraBornPortal, PlayStation Portal Cloud Streaming oturumlarında alınan ekran görüntülerindeki oyun metinlerini Android telefonda mümkün olduğunca otomatik biçimde algılayıp Türkçeye çevirmek için geliştirilen deneysel bir yardımcı uygulamadır.

## Hedef akış

PlayStation Portal → Create ile ekran görüntüsü → PlayStation Cloud Gallery → DraBornPortal → cihaz içi OCR → oyun terimi koruma → cihaz içi EN→TR çeviri → Türkçe sonuç

## v0.1 hedefleri

- Native Android / Kotlin / Jetpack Compose
- Ücretli çeviri API'si yok
- Google ML Kit Text Recognition ile cihaz içi Latin OCR
- Google ML Kit Translation ile cihaz içi İngilizce → Türkçe
- EN/TR modellerini önceden indirme ve durum göstergesi
- Oyun özel adlarını/terimlerini çeviri sırasında koruyan sözlük katmanı
- Tekrarlanan metinleri tekrar çevirmeme
- Çeviri geçmişi
- Telefonda yerel ekran görüntüsü seçerek uçtan uca OCR + çeviri testi
- Sony Cloud Gallery bağlayıcısı ayrı ve deneysel modül olarak geliştirilecek

## Önemli teknik not

PlayStation Portal, Cloud Streaming sırasında Create düğmesiyle alınan ekran görüntülerini Sony bulutuna yükleyebilir ve bunlar PlayStation App içindeki Captures bölümünden görülebilir. Sony'nin Cloud Gallery için herkese açık, belgelenmiş bir üçüncü taraf API'si bulunmadığından otomatik galeri erişimi deneysel olarak ele alınır ve Sony değişiklikleriyle bozulabilir.

İlk doğrulama aşamasında çeviri motoru, Android sistem fotoğraf seçicisinden verilen bir oyun ekran görüntüsünde tamamen yerel çalışır. Bu, Sony entegrasyonundan bağımsız olarak OCR ve çeviri kalitesini test etmemizi sağlar.

## Gizlilik

- Oyun ekran görüntüsü OCR ve çeviri için üçüncü taraf çeviri sunucusuna gönderilmez.
- ML Kit dil modeli bir kez cihaza indirildikten sonra çeviri cihaz üzerinde yapılır.
- Sony oturum belirteçleri ileriki entegrasyonda düz metin olarak repoya veya loglara yazılmayacaktır.

## Durum

**v0.1 / Android iskeleti kuruluyor.**
