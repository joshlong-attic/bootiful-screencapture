package com.example.screencapture

import java.awt.image.RenderedImage
import java.io.Closeable
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.ImageOutputStream

/**
 * Ported from Elliot Kroo's Java code to Kotlin.
 *
 * @author Elliot Kroo (elliot[at]kroo[dot]net)
 * @author Josh Long
 */
class GifSequenceWriter(
        outputStream: ImageOutputStream,
        imageType: Int,
        timeBetweenFramesMS: Long,
        loopContinuously: Boolean) : Closeable {

    private val gifWriter = ImageIO.getImageWritersBySuffix("gif").asSequence().toList()[0]
    private val imageWriteParam = gifWriter.defaultWriteParam
    private val imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType)
    private val imageMetaData = gifWriter.getDefaultImageMetadata(this.imageTypeSpecifier, this.imageWriteParam)

    init {

        val metaFormatName = imageMetaData.nativeMetadataFormatName
        val root = imageMetaData.getAsTree(metaFormatName) as IIOMetadataNode

        getNode(root, "GraphicControlExtension")
                .apply {
                    setAttribute("disposalMethod", "none")
                    setAttribute("userInputFlag", "FALSE")
                    setAttribute("transparentColorFlag", "FALSE")
                    setAttribute("delayTime", (timeBetweenFramesMS / 10).toString())
                    setAttribute("transparentColorIndex", "0")
                }

        getNode(root, "CommentExtensions")
                .apply {
                    setAttribute("CommentExtension", "Created by MAH")
                }

        getNode(root, "ApplicationExtensions")
                .appendChild(IIOMetadataNode("ApplicationExtension")
                        .apply {
                            setAttribute("applicationID", "NETSCAPE")
                            setAttribute("authenticationCode", "2.0")
                            val loop = if (loopContinuously) 0 else 1
                            userObject = byteArrayOf(0x1, (loop and 0xFF).toByte(), (loop shr 8 and 0xFF).toByte())
                        }
                )

        imageMetaData.setFromTree(metaFormatName, root)

        gifWriter.output = outputStream
        gifWriter.prepareWriteSequence(null)
    }

    fun addToSequence(img: RenderedImage) {
        gifWriter.writeToSequence(IIOImage(img, null, imageMetaData), imageWriteParam)
    }

    override fun close() {
        gifWriter.endWriteSequence()
    }

    private fun getNode(
            rootNode: IIOMetadataNode,
            nodeName: String): IIOMetadataNode {
        val nNodes = rootNode.length
        for (i in 0 until nNodes) {
            if (rootNode.item(i).nodeName.compareTo(nodeName, ignoreCase = true) == 0) {
                return rootNode.item(i) as IIOMetadataNode
            }
        }
        val node = IIOMetadataNode(nodeName)
        rootNode.appendChild(node)
        return node
    }
}