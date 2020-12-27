import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileWriter
import java.lang.Exception
import java.time.LocalDate
import java.time.Month
import java.time.Period
import java.time.chrono.ChronoPeriod
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.*

data class Record(var utility:String="", var file_date: LocalDate=LocalDate.now(), var cust_type:String="", var sco_rate:Double=0.0, var sco_eff_start:LocalDate= LocalDate.now(), var sco_eff_end:LocalDate=LocalDate.now(), var sco_incl_file_dt:Boolean=true, var choice_company:String="", var choice_tariff_code:String="", var contract_type:String="", var len_contract:String="", var choice_price_per_mcf:String="", var choice_monthly_fee:String="", var choice_etf:String="", var promo_offer:String="", var intro_offer:String="", var file_name:String="")

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
    val regexSCO = if (stdModernFormat) Regex("(?:Dominion's|CGO's|Vectren's|Duke's) (?:current )*(?:SCO|GCR)[^\$]+(\\\$[\\d\\.]+) per (MCF|Mcf|mcf|CCF|ccf|Ccf)[^-]+.{0,2}Effective\\s+([\\S]+)\\s([^,]+),\\s([\\S]+)\\s(?:through|to)\\s([\\S]+)\\s([^,]+),\\s([\\d]+).?\\s?")
                    else Regex("""Effective +(?<startMn>[A-z]+) (?<startDay>[\d]+), (?<startYear>[\d]+) through (?<endMn>[A-z]+) (?<endDay>[\d]+), (?<endYear>[\d]+) *.*?\$(?<rate>[\d.]+) per (?<units>[^\n]{3}+)""",RegexOption.MULTILINE)
    val scoMatches = regexSCO.findAll(noNewLineText)
    println(scoMatches.toList().count())

    var scoRate = 0.0
    var scoEffStart = LocalDate.now()
    var scoEffEnd = LocalDate.now()
    var scoIncludesDate = true

    data class SCORange(val startDate:LocalDate, val endDate:LocalDate, val scoRate:Double)
    val scoRanges = mutableListOf<SCORange>()

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
                scoRate = match.groupValues[1].slice(1..match.groupValues[1].length-1).replace(Regex("\\.{2,10}"),".").toDouble()
                scoEffStart = dtEff
                scoEffEnd = dtLastEff
            }

            if (units=="ccf") {
                scoRate = scoRate*10
            }

            scoRanges.add(SCORange(LocalDate.of(yrEff,mnEff,dayEff),LocalDate.of(yrLastEff,mnLastEff,dayLastEff),match.groupValues[1].slice(1..match.groupValues[1].length-1).replace(Regex("\\.{2,10}"),".").toDouble()))
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
                scoRate = groups["rate"]!!.value.replace(Regex("\\.{2,10}"),".").toDouble()
                scoEffStart = dtEff
                scoEffEnd = dtLastEff
            }

            if (units == "ccf") {
                scoRate = scoRate * 10
            }

            scoRanges.add(SCORange(LocalDate.of(yrEff,mnEff,dayEff),LocalDate.of(yrLastEff,mnLastEff,dayLastEff),groups["rate"]!!.value.replace(Regex("\\.{2,10}"),".").toDouble()))
        }
    }

    if (scoRate == 0.0) {
        val daysSinceEnd = mutableListOf<Int>()
        scoRanges.forEach{ rng ->
            daysSinceEnd.add(Period.between(rng.endDate, rec.file_date).get(ChronoUnit.DAYS).toInt())
        }
        if (daysSinceEnd.count()>0) {
            val minDaysIndex = daysSinceEnd.indexOf(daysSinceEnd.minOrNull()!!)
            scoRate = scoRanges[minDaysIndex].scoRate
            scoEffStart = scoRanges[minDaysIndex].startDate
            scoEffEnd = scoRanges[minDaysIndex].endDate
            scoIncludesDate = false
        }

    }

    rec.sco_rate = scoRate
    rec.sco_eff_start = scoEffStart
    rec.sco_eff_end = scoEffEnd
    rec.sco_incl_file_dt = scoIncludesDate

    text = text.replace(rec.utility+"\n","")

    val regexChoiceOffers = Regex("(?<company>.+)\\n.+\\nTariff Code: (?<tariff>.+?) *\\nRate Type: +(?<rateType>.+?) Term Length: (?<len>\\d+).+?\\n\\\$(?<rate>[\\d\\.]+) per (?<uom>.{0,3}) Monthly Fee: \\\$(?<monthlyFee>[\\d\\.]+) Early Termination Fee: \\\$(?<etf>[\\d\\.]+)\\n(?s).+?(?-s)This (?<promoOffer>is not|is) a promotional offer\\. *\\nThis (?<introOffer>is not|is) a introductory offer\\.",RegexOption.MULTILINE)
    val choiceOffersMatches = regexChoiceOffers.findAll(text)
    println(choiceOffersMatches.toList().count())
    choiceOffersMatches.forEach { match ->
        val groups = match.groups as MatchNamedGroupCollection
        rec.choice_company = groups["company"]!!.value
        rec.choice_tariff_code = groups["tariff"]!!.value
        rec.contract_type = groups["rateType"]!!.value
        rec.len_contract = groups["len"]!!.value
        rec.choice_price_per_mcf = groups["rate"]!!.value
        val choice_price_unit = groups["uom"]!!.value
        if (choice_price_unit.toLowerCase()=="ccf") {
            rec.choice_price_per_mcf = (rec.choice_price_per_mcf.toDouble() * 10).toString() //because it's really in ccf
        }
        rec.choice_monthly_fee = groups["monthlyFee"]!!.value
        rec.choice_etf = groups["etf"]!!.value
        rec.promo_offer = groups["promoOffer"]!!.value
        rec.intro_offer = groups["introOffer"]!!.value

        records.add(rec.copy())
    }

    doc.close()
}

fun processFile(file:File) {
    val rec:Record = Record()

    rec.file_name = file.name

    val fileSplitArray = file.nameWithoutExtension.split('_')
    rec.utility = fileSplitArray.slice(1..fileSplitArray.count()-1).joinToString(" ")

    rec.file_date = LocalDate.parse(fileSplitArray[0])

    if (rec.file_date.isAfter(LocalDate.of(2014,2,13))) {
        extractModern(file,rec)
    }
}

fun main(args: Array<String>) {
    var counter = 1
    val filepath = "/Users/wwelch/Downloads/PUCO Apples to Apples/Consolidated Commercial/"
//    val filepath = "/Users/wwelch/Downloads/PUCO Apples to Apples/tests/test20/"
    File(filepath).walk().forEach { file ->
        if (file.isDirectory) {
            return@forEach
        }
        if (file.extension!="pdf") {
            return@forEach
        }
        println(file.absolutePath)
//        try {
            processFile(file)
//        } catch (ex: Exception) {
//            println("Error file ${file.nameWithoutExtension}")
//            counter+=1
//            return@forEach
//        }
        println("Processed file $counter")
        counter+=1
    }
    var fileWriter: FileWriter = FileWriter("/Users/wwelch/Downloads/PUCO Apples to Apples/output_commercial.csv")
    var csvPrinter: CSVPrinter = CSVPrinter(fileWriter, CSVFormat.DEFAULT.withHeader("Utility","File Date","SCO Rate","SCO Effective Start","SCO Effective End","SCO Eff Dt Incl File Dt","Choice Company","Tariff Code","Contract Type","Length of Contract","Price per Mcf","Monthly Fee","ETF","Promo Offer","Intro Offer","File Name"))
    records.forEach { record ->
        val data = Arrays.asList(
            record.utility,
            record.file_date,
            record.sco_rate,
            record.sco_eff_start,
            record.sco_eff_end,
            record.sco_incl_file_dt,
            record.choice_company,
            record.choice_tariff_code,
            record.contract_type,
            record.len_contract,
            record.choice_price_per_mcf,
            record.choice_monthly_fee,
            record.choice_etf,
            record.promo_offer,
            record.intro_offer,
            record.file_name
        )
        csvPrinter.printRecord(data)
    }
    fileWriter!!.flush()
    fileWriter.close()
    csvPrinter!!.close()
    println("Done!")
}