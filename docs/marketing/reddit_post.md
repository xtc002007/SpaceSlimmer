# Reddit r/androidapps Community Launch Post (Showoff Saturday)

**Subreddit**: `r/androidapps`
**Flair**: `[DEV] Showoff Saturday`
**Target Time**: Saturday 14:00 UTC (09:00 EST / 22:00 Beijing)

---

## Post Title:
`[DEV] I built SpaceSlimmer — a 100% offline, GPU-accelerated video & photo compressor with zero internet permissions, a 30-day Safe Vault undo, and a visual storage treemap`

---

## Post Content:

Hey r/androidapps! 👋

Like many of you with 64GB, 128GB, or full camera rolls, I was fed up with generic "cleaner" and "compressor" apps on Google Play that:
1. Demand intrusive `INTERNET` and tracking permissions just to compress an offline video.
2. Silently delete or overwrite your original 4K clips with no way to undo if the compression looks blurry.
3. Lock basic 1080p/HEVC encoding behind predatory subscriptions.

So I spent the last few months building **SpaceSlimmer** — a modern, privacy-first Android media compressor and visual storage analyzer.

### 🌟 What makes it different?

* **100% On-Device & Zero Internet Permissions**: Look at the Android Manifest — zero `android.permission.INTERNET`. Your family videos, private photos, and camera roll physically never leave your phone.
* **30-Day Safe Recovery Vault (Undo Anytime)**: Afraid of losing quality? When you choose to replace original files, SpaceSlimmer safely keeps the original uncompressed files in a local isolated sandbox for 7 to 30 days. If you ever change your mind, restore them with 1 tap.
* **Split-Screen Before & After Curtain Slider**: Scrub through your video side-by-side in real-time before saving to make sure faces and fine details stay razor-sharp.
* **Visual Storage Treemap**: Stop guessing where your storage went. SpaceSlimmer draws an interactive squarified block map showing your folders and gigabyte-hogging videos at a glance.
* **Presets for WhatsApp & Discord**: Need to send a clip on WhatsApp (16MB limit) or Discord (25MB)? SpaceSlimmer automatically calculates the optimal bitrate to guarantee your video fits under the limit without cropping.
* **Tiny & Fast**: Less than 7MB download size, compiled with R8 minification and GPU hardware acceleration.

### 📥 Download on Google Play:
[SpaceSlimmer on Google Play Store](https://play.google.com/store/apps/details?id=com.spaceslimmer.app)

It’s completely free, with no ads and no tracking. I'd love to hear your feedback, bug reports, and feature suggestions! Let me know what you think in the comments.
