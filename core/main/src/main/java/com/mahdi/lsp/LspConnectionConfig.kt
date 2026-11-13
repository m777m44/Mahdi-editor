package com.mahdi.lsp

import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider

fun interface ConnectionProviderFactory {
    fun create(instance: LspServerInstance): BaseLspConnectionProvider
}

abstract class BaseLspConnectionProvider(protected val instance: LspServerInstance) : StreamConnectionProvider

sealed interface LspConnectionConfig {
    fun providerFactory(): ConnectionProviderFactory

    data class Socket(val host: String = "localhost", val port: Int) : LspConnectionConfig {
        override fun providerFactory() = ConnectionProviderFactory { SocketConnection(port, host, it) }
    }

    /**
     * خادم يعمل داخل الحاوية عبر `proot`.
     *
     * 9.1.0: أُضيفت [env] لأن خوادم Node (Pyright · TypeScript · ESLint …) كانت
     * تعمل بحدّ الذاكرة الافتراضي لـV8 (يكبر حتى ~٢ غيغابايت). على هاتف بذاكرة
     * ٣–٤ غيغابايت كان ذلك يجعل قاتل الذاكرة يقتل الجلسة (SIGKILL / إشارة ٩)
     * أثناء تشغيل سكربت. الآن يُمرَّر حدّ صريح هادئ.
     */
    data class Process(
        val command: Array<String>,
        val env: Map<String, String> = emptyMap(),
    ) : LspConnectionConfig {
        override fun providerFactory() = ConnectionProviderFactory { ProcessConnection(command, env, it) }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Process

            return command.contentEquals(other.command) && env == other.env
        }

        override fun hashCode(): Int {
            return 31 * command.contentHashCode() + env.hashCode()
        }
    }

    data class AndroidProcess(val command: Array<String>) : LspConnectionConfig {
        override fun providerFactory() = ConnectionProviderFactory { AndroidProcessConnection(command, it) }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AndroidProcess

            return command.contentEquals(other.command)
        }

        override fun hashCode(): Int {
            return command.contentHashCode()
        }
    }

    data class Custom(val provider: ConnectionProviderFactory) : LspConnectionConfig {
        override fun providerFactory() = provider
    }
}
