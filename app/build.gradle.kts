plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  // cold-start-perf F.1 — apply the Baseline Profile plugin so the
  // `:app:generateBaselineProfile` task is wired and the consumed profile
  // is packaged into the release APK. The sibling `:baselineprofile`
  // module produces the rules; this side consumes them.
  alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.eight87.whisperboy"
    compileSdk = 36
    base {
        archivesName.set("whisperboy")
    }
    defaultConfig {
        applicationId = "com.eight87.whisperboy"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.process)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // material-icons-extended — needed for non-core glyphs (MenuBook, GridView, ViewList, MoreVert,
  // Folder, etc.) used across the library / settings / player chrome. Not transitive in material3
  // 1.4.0+ (m3-expressive.md gotcha #2).
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 — ExoPlayer + MediaSession + MediaLibraryService for Android Auto (Phase N).
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  // Phase N — `kotlinx.coroutines.guava.future { ... }` so the MediaLibrarySession callback
  // can express its async lookups as suspend functions and return them as the
  // `ListenableFuture<LibraryResult<...>>` Media3 expects.
  implementation(libs.kotlinx.coroutines.guava)

  // SAF — DocumentFile wrapper for the picked-tree URIs (Phase C wraps this in CachedDocumentFile).
  implementation(libs.androidx.documentfile)

  // Palette — extract a dominant swatch from the cover bitmap for the F.6 player background
  // gradient. Apache-2.0, decode happens off-main (Dispatchers.IO in PaletteTint.kt).
  implementation(libs.androidx.palette)

  // Coil — cover-art tile loading (cover-art.md Phase A.5). Local files only; no
  // coil-network-okhttp dep — network image search is cover-art.md Phase B work.
  implementation(libs.coil.compose)
  // cover-art Phase B — Coil's OkHttp network fetcher, so the staggered-grid thumbnails
  // (HTTP URLs from DuckDuckGo) load via Coil. Without this fetcher Coil-3 fails any
  // non-file `model`.
  implementation(libs.coil.network.okhttp)

  // DataStore Preferences — persisted (treeUri → FolderType) mapping in Phase C, settings in Phase K.
  implementation(libs.androidx.datastore.preferences)

  // Room — Phase D's library cache (BookEntity / ChapterEntity / BookmarkEntity).
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.androidx.room.testing)

  // cover-art Phase B — user-initiated DuckDuckGo image search for per-book cover art.
  // Retrofit + OkHttp + kotlinx-serialization power the two-step `vqd` protocol (see
  // docs/plans/cover-art.md); androidx.paging threads the `next` cursor into
  // `LazyVerticalStaggeredGrid` via paging-compose's `LazyPagingItems`.
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation(libs.retrofit.converter.scalars)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.paging.runtime)
  implementation(libs.androidx.paging.compose)

  // cold-start-perf F.3 — profileinstaller picks up the baseline-prof.txt
  // packaged into the APK and hands it to ART at app install time, which is
  // the mechanism that gives us the ~25–35% cold-start win.
  implementation(libs.androidx.profileinstaller)

  // cold-start-perf F.1 — declare the sibling test module as the producer
  // of the baseline profile this app consumes.
  "baselineProfile"(project(":baselineprofile"))
}
