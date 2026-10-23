package com.oro.app.data.repository

import com.oro.app.data.local.entity.ProjectTemplate
import com.oro.app.data.repository.templates.AndroidTemplates
import com.oro.app.data.repository.templates.BasicHtmlTemplates
import com.oro.app.data.repository.templates.ExpressTemplates
import com.oro.app.data.repository.templates.NextJsTemplates
import com.oro.app.data.repository.templates.PhaserTemplates
import com.oro.app.data.repository.templates.PwaTemplates
import com.oro.app.data.repository.templates.ReactTemplates
import com.oro.app.data.repository.templates.StudioTemplates
import com.oro.app.data.repository.templates.SvelteTemplates
import com.oro.app.data.repository.templates.TailwindTemplates
import com.oro.app.data.repository.templates.ThreeJsTemplates
import com.oro.app.data.repository.templates.VueTemplates
import java.io.File

/**
 * Generates template files for new projects.
 * Each template creates appropriate file structure and boilerplate code.
 */
object ProjectTemplateGenerator {

    /**
     * Create template files for a project
     */
    fun createTemplateFiles(projectDir: File, template: ProjectTemplate) {
        when (template) {
            ProjectTemplate.BLANK -> createBlankTemplate(projectDir)
            ProjectTemplate.HTML_BASIC -> createBasicHtmlTemplate(projectDir)
            ProjectTemplate.TAILWIND -> createTailwindTemplate(projectDir)
            ProjectTemplate.LANDING_PAGE -> StudioTemplates.install(projectDir, StudioTemplates.LANDING)
            ProjectTemplate.REACT -> createReactTemplate(projectDir)
            ProjectTemplate.VUE -> createVueTemplate(projectDir)
            ProjectTemplate.SVELTE -> createSvelteTemplate(projectDir)
            ProjectTemplate.NEXT_JS -> createNextJsTemplate(projectDir)
            ProjectTemplate.ANDROID_APP -> createAndroidAppTemplate(projectDir)
            ProjectTemplate.PWA -> createPwaTemplate(projectDir)
            ProjectTemplate.PHASER -> createPhaserTemplate(projectDir)
            ProjectTemplate.THREE_JS -> createThreeJsTemplate(projectDir)
            ProjectTemplate.EXPRESS_API -> createExpressApiTemplate(projectDir)
            ProjectTemplate.PORTFOLIO -> StudioTemplates.install(projectDir, StudioTemplates.PORTFOLIO)
            ProjectTemplate.BLOG -> StudioTemplates.install(projectDir, StudioTemplates.BLOG)
            ProjectTemplate.DASHBOARD -> StudioTemplates.install(projectDir, StudioTemplates.DASHBOARD)
            ProjectTemplate.STORE -> StudioTemplates.install(projectDir, StudioTemplates.STORE)
            ProjectTemplate.DOCS -> StudioTemplates.install(projectDir, StudioTemplates.DOCS)
            ProjectTemplate.ARCADE -> StudioTemplates.install(projectDir, StudioTemplates.ARCADE)
        }
    }

    private fun createBlankTemplate(projectDir: File) {
        // Blank template creates no files - just an empty project directory
    }

    private fun createBasicHtmlTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(BasicHtmlTemplates.HTML)
        File(projectDir, "style.css").writeText(BasicHtmlTemplates.CSS)
        File(projectDir, "script.js").writeText(BasicHtmlTemplates.JS)
    }

    private fun createTailwindTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(TailwindTemplates.HTML)
        File(projectDir, "style.css").writeText(TailwindTemplates.CSS)
    }

    private fun createReactTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(ReactTemplates.HTML)
        File(projectDir, "src").mkdir()
        File(projectDir, "src/App.jsx").writeText(ReactTemplates.APP_JSX)
        File(projectDir, "src/main.jsx").writeText(ReactTemplates.MAIN_JSX)
        File(projectDir, "src/App.css").writeText(ReactTemplates.APP_CSS)
        File(projectDir, "package.json").writeText(ReactTemplates.PACKAGE_JSON)
        File(projectDir, "vite.config.js").writeText(ReactTemplates.VITE_CONFIG)
    }

    private fun createVueTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(VueTemplates.HTML)
        File(projectDir, "src").mkdir()
        File(projectDir, "src/App.vue").writeText(VueTemplates.APP_VUE)
        File(projectDir, "src/main.js").writeText(VueTemplates.MAIN_JS)
        File(projectDir, "src/style.css").writeText(VueTemplates.STYLE_CSS)
        File(projectDir, "package.json").writeText(VueTemplates.PACKAGE_JSON)
        File(projectDir, "vite.config.js").writeText(VueTemplates.VITE_CONFIG)
    }

    private fun createSvelteTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(SvelteTemplates.HTML)
        File(projectDir, "src").mkdir()
        File(projectDir, "src/App.svelte").writeText(SvelteTemplates.APP_SVELTE)
        File(projectDir, "src/main.js").writeText(SvelteTemplates.MAIN_JS)
        File(projectDir, "src/app.css").writeText(SvelteTemplates.APP_CSS)
        File(projectDir, "package.json").writeText(SvelteTemplates.PACKAGE_JSON)
        File(projectDir, "vite.config.js").writeText(SvelteTemplates.VITE_CONFIG)
    }

    private fun createNextJsTemplate(projectDir: File) {
        File(projectDir, "pages").mkdir()
        File(projectDir, "pages/index.js").writeText(NextJsTemplates.INDEX_JS)
        File(projectDir, "pages/_app.js").writeText(NextJsTemplates.APP_JS)
        File(projectDir, "styles").mkdir()
        File(projectDir, "styles/globals.css").writeText(NextJsTemplates.GLOBALS_CSS)
        File(projectDir, "styles/Home.module.css").writeText(NextJsTemplates.HOME_MODULE_CSS)
        File(projectDir, "package.json").writeText(NextJsTemplates.PACKAGE_JSON)
        File(projectDir, "next.config.js").writeText(NextJsTemplates.NEXT_CONFIG)
    }

    private fun createAndroidAppTemplate(projectDir: File) {
        // Create directory structure
        File(projectDir, "app/src/main/java/com/example/app").mkdirs()
        File(projectDir, "app/src/main/res/layout").mkdirs()
        File(projectDir, "app/src/main/res/values").mkdirs()
        File(projectDir, "app/src/main/res/drawable").mkdirs()
        File(projectDir, ".github/workflows").mkdirs()

        // Root files
        File(projectDir, "build.gradle.kts").writeText(AndroidTemplates.ROOT_BUILD_GRADLE)
        File(projectDir, "settings.gradle.kts").writeText(AndroidTemplates.SETTINGS_GRADLE)
        File(projectDir, "gradle.properties").writeText(AndroidTemplates.GRADLE_PROPERTIES)

        // App module files
        File(projectDir, "app/build.gradle.kts").writeText(AndroidTemplates.APP_BUILD_GRADLE)
        File(projectDir, "app/src/main/AndroidManifest.xml").writeText(AndroidTemplates.MANIFEST)
        File(projectDir, "app/src/main/java/com/example/app/MainActivity.kt")
            .writeText(AndroidTemplates.MAIN_ACTIVITY)

        // Resources
        File(projectDir, "app/src/main/res/layout/activity_main.xml")
            .writeText(AndroidTemplates.ACTIVITY_MAIN_XML)
        File(projectDir, "app/src/main/res/values/strings.xml")
            .writeText(AndroidTemplates.STRINGS_XML)
        File(projectDir, "app/src/main/res/values/themes.xml")
            .writeText(AndroidTemplates.THEMES_XML)
        File(projectDir, "app/src/main/res/values/colors.xml")
            .writeText(AndroidTemplates.COLORS_XML)

        // GitHub Actions workflow for APK builds
        File(projectDir, ".github/workflows/build-apk.yml")
            .writeText(AndroidTemplates.GITHUB_WORKFLOW)

        // README with build instructions
        File(projectDir, "README.md").writeText(AndroidTemplates.README)
    }

    private fun createPwaTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(PwaTemplates.HTML)
        File(projectDir, "style.css").writeText(PwaTemplates.CSS)
        File(projectDir, "app.js").writeText(PwaTemplates.APP_JS)
        File(projectDir, "sw.js").writeText(PwaTemplates.SERVICE_WORKER)
        File(projectDir, "manifest.json").writeText(PwaTemplates.MANIFEST)
    }

    private fun createPhaserTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(PhaserTemplates.HTML)
        File(projectDir, "style.css").writeText(PhaserTemplates.CSS)
        File(projectDir, "src").mkdir()
        File(projectDir, "src/game.js").writeText(PhaserTemplates.GAME_JS)
        File(projectDir, "src/scenes").mkdir()
        File(projectDir, "src/scenes/MainScene.js").writeText(PhaserTemplates.MAIN_SCENE)
    }

    private fun createThreeJsTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(ThreeJsTemplates.HTML)
        File(projectDir, "style.css").writeText(ThreeJsTemplates.CSS)
        File(projectDir, "main.js").writeText(ThreeJsTemplates.MAIN_JS)
    }

    private fun createExpressApiTemplate(projectDir: File) {
        File(projectDir, "index.js").writeText(ExpressTemplates.INDEX_JS)
        File(projectDir, "package.json").writeText(ExpressTemplates.PACKAGE_JSON)
        File(projectDir, "routes").mkdir()
        File(projectDir, "routes/api.js").writeText(ExpressTemplates.API_ROUTES)
        File(projectDir, ".env.example").writeText(ExpressTemplates.ENV_EXAMPLE)
    }

}
