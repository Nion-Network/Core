package utils

import logging.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

class Crypto(private val keystorePath: String) {

    // TODO make immutable, fix public key property.

    private var keyPair: KeyPair
    var encodedPublicKey: ByteArray
        private set
    var publicKey: String
        private set
    var hashedPublicKey: ByteArray
        private set

    /** Signs data using the keypair from [keystorePath]. */
    @Throws(Exception::class)
    fun sign(data: ByteArray?): ByteArray {
        val privateSignature = Signature.getInstance("SHA256withRSA")
        privateSignature.initSign(keyPair.private)
        privateSignature.update(data)
        return privateSignature.sign()
    }

    /** Verifies whether the data signed by the public key equals to the signature. */
    @Throws(Exception::class)
    fun verify(data: ByteArray?, signature: ByteArray?, publicKey: String?): Boolean {
        val byteKey = Base64.getMimeDecoder().decode(publicKey)
        val publicSignature = Signature.getInstance("SHA256withRSA")
        val X509publicKey = X509EncodedKeySpec(byteKey)
        val kf = KeyFactory.getInstance("RSA")
        publicSignature.initVerify(kf.generatePublic(X509publicKey))
        publicSignature.update(data)
        return publicSignature.verify(signature)
    }

    /** Stores the [keyPair] in [path] */
    @Throws(IOException::class)
    fun saveKeyPair(path: String, keyPair: KeyPair) {
        val privateKey = keyPair.private
        val publicKey = keyPair.public

        // Store Public Key.
        val x509EncodedKeySpec = X509EncodedKeySpec(
            publicKey.encoded
        )
        var fos = FileOutputStream("$path/public.key")
        fos.write(x509EncodedKeySpec.encoded)
        fos.close()

        // Store Private Key.
        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(
            privateKey.encoded
        )
        fos = FileOutputStream("$path/private.key")
        fos.write(pkcs8EncodedKeySpec.encoded)
        fos.close()
    }

    /** Loads and returns the keypair stored in [path] or throws an exception if the pair does not exist. */
    @Throws(Exception::class)
    fun loadKeyPair(path: String): KeyPair {
        // Read Public Key.
        val filePublicKey = File("$path/public.key")
        var fis = FileInputStream("$path/public.key")
        val encodedPublicKey = ByteArray(filePublicKey.length().toInt())
        fis.read(encodedPublicKey)
        fis.close()

        // Read Private Key.
        val filePrivateKey = File("$path/private.key")
        fis = FileInputStream("$path/private.key")
        val encodedPrivateKey = ByteArray(filePrivateKey.length().toInt())
        fis.read(encodedPrivateKey)
        fis.close()

        // Generate KeyPair.
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(encodedPublicKey)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        val privateKeySpec = PKCS8EncodedKeySpec(encodedPrivateKey)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)
        return KeyPair(publicKey, privateKey)
    }

    init {
        keyPair = try {
            loadKeyPair(keystorePath)
        } catch (e: Exception) {
            Logger.error("Could not load key pair: $e.")
            Logger.info("Generating new key pair.")
            val generatedPair = KeyPairGenerator.getInstance("RSA").let {
                it.initialize(2048, SecureRandom())
                it.generateKeyPair()
            }
            // saveKeyPair(keystorePath, generatedPair)
            generatedPair
        }
        encodedPublicKey = keyPair.public.encoded
        hashedPublicKey = Utils.sha256(encodedPublicKey)
        publicKey = String(Base64.getMimeEncoder().encode(encodedPublicKey))
    }
}