import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileWriter
import java.lang.Exception
import java.time.LocalDate
import java.time.Month
import java.util.*

data class Record(var utility:String="", var file_date: LocalDate=LocalDate.now(), var cust_type:String="", var sco_rate:Double=0.0, var sco_eff_start:LocalDate= LocalDate.now(), var sco_eff_end:LocalDate=LocalDate.now(), var choice_company:String="", var contract_type:String="", var len_contract:String="", var choice_price_per_mcf:String="", var choice_monthly_fee:String="", var choice_etf:String="", var file_name:String="")

data class Plan(var company:String="", var planId:String="", var baseRate:String="", var totalRate:String="", var length:String="", var etf:String="", var type:String="")

val records = mutableListOf<Record>()

fun extractModern(file:File, rec:Record) {
    val doc: PDDocument = PDDocument.load(file)
    val pdfTextStripper = PDFTextStripper()
    pdfTextStripper.sortByPosition = true
    var text = pdfTextStripper.getText(doc)
    text = text.replace("‘","'")
    text = text.replace("’","'")

    // Remove page header text
//    val regexPageHeader = Regex("page [^\\n]+\\nPub[^\\n]+\\n[^\\n]+\n")
    val regexPageHeader = Regex("page [^\\n]+\\nPub[^\\n]+ ?\\n(?:Dominion East Ohio|Dominion Energy Ohio|Columbia Gas of Ohio|Vectren Energy Delivery|Duke Energy Ohio) ?\\n")
    text = text.replace(regexPageHeader, "")

    val noNewLineText = text.replace("\n"," ")

    val stdModernFormat = (rec.utility!="Columbia Gas of Ohio") || (rec.utility=="Columbia Gas of Ohio" && (rec.file_date.isBefore(LocalDate.of(2016,5,13)) || rec.file_date.isAfter(LocalDate.of(2018,8,30))))
    val regexSCO = if (stdModernFormat) Regex("(?:Dominion's|CGO's|Vectren's|Duke's) (?:current )*(?:SCO|GCR)[^\$]+(\\\$\\d\\.[\\d]+) per (MCF|Mcf|mcf|CCF|ccf|Ccf)[^-]+.{0,2}Effective\\s+([\\S]+)\\s([^,]+),\\s([\\S]+)\\s(?:through|to)\\s([\\S]+)\\s([^,]+),\\s([\\d]+).?\\s?")
                    else Regex("""Effective +(?<startMn>[A-z]+) (?<startDay>[\d]+), (?<startYear>[\d]+) through (?<endMn>[A-z]+) (?<endDay>[\d]+), (?<endYear>[\d]+) *.*?\$(?<rate>[\d.]+) per (?<units>[^\n]{3}+)""",RegexOption.MULTILINE)
    val scoMatches = regexSCO.findAll(noNewLineText)
    println(scoMatches.toList().count())

    var scoRate = 0.0
    var scoEffStart = LocalDate.now()
    var scoEffEnd = LocalDate.now()

    scoMatches.forEach { match ->
        if (scoRate>0.0) {
            return@forEach
        }
        if (stdModernFormat) {
            //        println(match.groupValues[1]) // rate
            //        println(match.groupValues[2]) // units
            //        println(match.groupValues[3]) // month effective
            //        println(match.groupValues[4]) // day effective
            //        println(match.groupValues[5]) // year effective
            //        println(match.groupValues[6]) // month last effective
            //        println(match.groupValues[7]) // day last effective
            //        println(match.groupValues[8]) // year last effective
            val mnEff = Month.valueOf(match.groupValues[3].toUpperCase()).value
            val dayEff = match.groupValues[4].trim().toInt()
            val yrEff = match.groupValues[5].trim().toInt()

            val mnLastEff = Month.valueOf(match.groupValues[6].toUpperCase()).value
            val dayLastEff = match.groupValues[7].trim().toInt()
            val yrLastEff = match.groupValues[8].trim().toInt()

            val units = match.groupValues[2].toLowerCase()

            val dtEff = LocalDate.of(yrEff,mnEff,dayEff)
            var dtLastEff = LocalDate.of(yrLastEff,mnLastEff,dayLastEff)

            if (dtLastEff.isBefore(dtEff)) {
                dtLastEff = LocalDate.of(yrLastEff+1,mnLastEff,dayLastEff)
            }

            if (rec.file_date.compareTo(dtEff)>=0 && rec.file_date.compareTo(dtLastEff)<=0) {
                scoRate = match.groupValues[1].slice(1..match.groupValues[1].length-1).toDouble()
                scoEffStart = dtEff
                scoEffEnd = dtLastEff
            }

            if (units=="ccf") {
                scoRate = scoRate*10
            }
        } else {
            val groups = match.groups as MatchNamedGroupCollection
            val mnEff = Month.valueOf(groups["startMn"]!!.value.toUpperCase()).value
            val dayEff = groups["startDay"]!!.value.trim().toInt()
            val yrEff = groups["startYear"]!!.value.trim().toInt()

            val mnLastEff = Month.valueOf(groups["endMn"]!!.value.toUpperCase()).value
            val dayLastEff = groups["endDay"]!!.value.trim().toInt()
            val yrLastEff = groups["endYear"]!!.value.trim().toInt()

            val units = groups["units"]!!.value.toLowerCase()

            val dtEff = LocalDate.of(yrEff, mnEff, dayEff)
            var dtLastEff = LocalDate.of(yrLastEff, mnLastEff, dayLastEff)

            if (dtLastEff.isBefore(dtEff)) {
                dtLastEff = LocalDate.of(yrLastEff + 1, mnLastEff, dayLastEff)
            }

            if (rec.file_date.compareTo(dtEff) >= 0 && rec.file_date.compareTo(dtLastEff) <= 0) {
                scoRate = groups["rate"]!!.value.toDouble()
                scoEffStart = dtEff
                scoEffEnd = dtLastEff
            }

            if (units == "ccf") {
                scoRate = scoRate * 10
            }
        }
    }

    rec.sco_rate = scoRate
    rec.sco_eff_start = scoEffStart
    rec.sco_eff_end = scoEffEnd

    val regexChoiceOffers = Regex("([^\\n]+)\\n\\([\\d]+\\)\\s[\\d]+\\-[\\d]+\\nRate Type: (.+)\\sLength:\\s([\\d]+)\\s[^\\n]+\\n\\\$([\\d\\.]+) per (Ccf|Mcf) Monthly Fee: \\\$([\\d\\.]+) Early Termination Fee: \\\$([\\d\\.]+)[^\\n]*\\n")
    val choiceOffersMatches = regexChoiceOffers.findAll(text)
    println(choiceOffersMatches.toList().count())
    choiceOffersMatches.forEach { match ->
//        println(match.groupValues[0])
//        println(match.groupValues[1]) // company
//        println(match.groupValues[2]) // type of contract
//        println(match.groupValues[3]) // length of contract
//        println(match.groupValues[4]) // price
//        println(match.groupValues[5]) // unit
//        println(match.groupValues[6]) // monthly fee
//        println(match.groupValues[7]) // early termination fee
        rec.choice_company = match.groupValues[1]
        rec.contract_type = match.groupValues[2]
        rec.len_contract = match.groupValues[3]
        rec.choice_price_per_mcf = match.groupValues[4]
        val choice_price_unit = match.groupValues[5]
        if (choice_price_unit.toLowerCase()=="ccf") {
            rec.choice_price_per_mcf = (rec.choice_price_per_mcf.toDouble() * 10).toString() //because it's really in ccf
        }
        rec.choice_monthly_fee = match.groupValues[6]
        rec.choice_etf = match.groupValues[7]
        records.add(rec.copy())
    }

    doc.close()
}

fun processFile(file:File) {
    val rec:Record = Record()

    rec.file_name = file.name

    val fileSplitArray = file.nameWithoutExtension.split('_')
    rec.utility = fileSplitArray.slice(1..fileSplitArray.count()-2).joinToString(" ")

    rec.file_date = LocalDate.parse(fileSplitArray[0])

    if (rec.file_date.isAfter(LocalDate.of(2014,2,13))) {
        extractModern(file,rec)
    }
}

fun main(args: Array<String>) {
    var counter = 1
    val filepath = "/Users/wwelch/Downloads/PUCO Apples to Apples/Consolidated/"
//    val filepath = "/Users/wwelch/Downloads/PUCO Apples to Apples/tests/test20/"
    File(filepath).walk().forEach { file ->
        if (file.isDirectory) {
            return@forEach
        }
        if (file.extension!="pdf") {
            return@forEach
        }
        println(file.absolutePath)
        try {
            processFile(file)
        } catch (ex: Exception) {
            println("Error file ${file.nameWithoutExtension}")
            counter+=1
            return@forEach
        }
        println("Processed file $counter")
        counter+=1
    }
    var fileWriter: FileWriter = FileWriter("/Users/wwelch/Downloads/PUCO Apples to Apples/output.csv")
    var csvPrinter: CSVPrinter = CSVPrinter(fileWriter, CSVFormat.DEFAULT.withHeader("Utility","File Date","SCO Rate","SCO Effective Start","SCO Effective End","Choice Company","Contract Type","Length of Contract","Price per Mcf","Monthly Fee","ETF","File Name"))
    records.forEach { record ->
        val data = Arrays.asList(
            record.utility,
            record.file_date,
            record.sco_rate,
            record.sco_eff_start,
            record.sco_eff_end,
            record.choice_company,
            record.contract_type,
            record.len_contract,
            record.choice_price_per_mcf,
            record.choice_monthly_fee,
            record.choice_etf,
            record.file_name
        )
        csvPrinter.printRecord(data)
    }
    fileWriter!!.flush()
    fileWriter.close()
    csvPrinter!!.close()
    println("Done!")
}