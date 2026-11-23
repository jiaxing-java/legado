package io.legado.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        configureAndroidJvm21Toolchain()

        extensions.configure<ApplicationExtension> {
            // 37.2: miuix 的 AAR 元数据要求 (见 app/build.gradle.kts 同处注释)
            compileSdk {
                version = release(37) {
                    minorApiLevel = 2
                }
            }
            defaultConfig {
                minSdk = 24
                targetSdk = 36
            }
            compileOptions {
                configureJavaCompat21()
            }
        }
    }
}
