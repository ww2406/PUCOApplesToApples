import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.lang.Exception

fun main(args: Array<String>) {
    File("/Users/wwelch/Downloads/PUCO Apples to Apples/tests/test23/").walk().forEach { file ->
        if (file.isDirectory) {
            return@forEach
        }
        if (file.extension!="pdf") {
            return@forEach
        }
        val doc: PDDocument = PDDocument.load(file)
        val pdfTextStripper = PDFTextStripper()
        pdfTextStripper.sortByPosition = true
        var text = pdfTextStripper.getText(doc)
//        text = text.slice(text.indexOf("Supplier Plans, Rates, Terms and Descriptions")..text.length-1)
        val regexPageHeader = Regex("page [^\\n]+\\nPub[^\\n]+ ?\\n")
        text = text.replace(regexPageHeader, "")
        println(text)
        println(text.indexOf("Supplier Plans, Rates, Terms and Descriptions"))
    }
}