import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.Month
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.lang.Exception
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*
import java.time.temporal.TemporalAdjusters;

data class Record(var utility:String="", var file_date: LocalDate=LocalDate.now(), var cust_type:String="", var sco_rate:Double=0.0, var sco_eff_start:LocalDate= LocalDate.now(), var sco_eff_end:LocalDate=LocalDate.now(), var choice_company:String="", var contract_type:String="", var len_contract:String="", var choice_price_per_mcf:String="", var choice_monthly_fee:String="", var choice_etf:String="", var file_name:String="")

data class Plan(var company:String="", var planId:String="", var baseRate:String="", var totalRate:String="", var length:String="", var etf:String="", var type:String="")

data class DukeOldDTO(
    var type:String="",
    var lineNum:Int,
    var value:MutableMap<String,String>
)

// grp[1] := choice company name
    // grp[2] := supplier base rate
    // grp[3] := supplier total rate
    // grp[4] := thru month
    // grp[5] := thru year
    // grp[6] := etf
enum class thruRegexCols(val num:Int) {
    COMPANY(1),
    SUP_BASE_RATE(2),
    SUP_TOT_RATE(3),
    THRU_MN(4),
    THRU_YR(5),
    ETF(6)
}

enum class stdRegexCols(val num:Int) {
    COMPANY(1),
    SUP_BASE_RATE(2),
    SUP_TOT_RATE(3),
    CT_LENGTH(4),
    ETF(5),
}

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

fun extractMiddle(file:File, rec:Record) {
    val doc: PDDocument = PDDocument.load(file)
    val pdfTextStripper = PDFTextStripper()
    pdfTextStripper.sortByPosition = true
    var text = pdfTextStripper.getText(doc)
//    text = text.slice(text.indexOf("Supplier Plans, Rates, Terms and Descriptions")..text.length-1)
    val spChar = if (rec.file_date.isAfter(LocalDate.of(2009,12,23)) && rec.file_date.isBefore(LocalDate.of(2012,8,3))) "" else " "
    text = text.replace("Monthly(?! Variable)".toRegex(RegexOption.MULTILINE), "1$spChar-Month")

    val abbrevs = mapOf<String,String>("Dominion Energy Ohio" to "DEO", "Columbia Gas of Ohio" to "Columbia Gas","Duke Energy Ohio" to "Duke", "Vectren Energy Delivery" to "Vectren")

    val scoPattern = if (rec.utility == "Dominion Energy Ohio")  "current(?: total)*(?: SCO)* rate is \\\$ *([\\d.]+) per.*?(ccf|mcf|MCF|CCF|Ccf|Mcf)[ \\.)\\s]+Effective(?: from)* ([\\d]{2}\\/[\\d]{2}\\/[\\d]{4}) through ([\\d]{2}\\/[\\d]{2}\\/[\\d]{4})"
                    else "current (?:SCO )*total(?: SCO| GCR)* rate is \\\$ *([\\d.]+) per.*?(ccf|mcf|MCF|CCF|Ccf|Mcf)[ )\\s]+Effective(?: from)* ([\\S]+)\\s([^,]+),\\s([\\S]+)\\s(?:through|to)\\s([\\S]+)\\s([^,]+),\\s([\\S]+)\\s"
    val scoRegex:Regex = Regex(scoPattern, RegexOption.IGNORE_CASE)
    val scoMatch = scoRegex.find(text)
    // match.groupValues[1] := total SCO
    // match.groupValues[2] := SCO units
    // match.groupValues[3] := eff start
    // match.groupValues[4] := eff end
    println(rec.utility)
    var unitsMultiplier=1 // will change to 10 if convert ccf->mcf
    if (scoMatch!=null) {
        rec.sco_rate = scoMatch.groupValues[1].toDouble()
        if (scoMatch.groupValues[2].toLowerCase()=="ccf") {
            rec.sco_rate *= 10
            unitsMultiplier = 10
        }
        if (rec.utility=="Dominion Energy Ohio") {
            rec.sco_eff_start = LocalDate.parse(scoMatch.groupValues[3], DateTimeFormatter.ofPattern("MM/dd/yyyy"))
            rec.sco_eff_end = LocalDate.parse(scoMatch.groupValues[4], DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        } else {
            rec.sco_eff_start = LocalDate.of(scoMatch.groupValues[5].toInt(),Month.valueOf(scoMatch.groupValues[3].toUpperCase()),scoMatch.groupValues[4].toInt())
            rec.sco_eff_end = LocalDate.of(scoMatch.groupValues[8].toInt(),Month.valueOf(scoMatch.groupValues[6].toUpperCase()),scoMatch.groupValues[7].toInt())
        }

    }
    println(rec.sco_rate)
    println(rec.sco_eff_start)
    println(rec.sco_eff_end)

    val thruRegex = Regex("^([^\$\\n]*)\\s*\\\$ ?([\\d.]+) \\\$ ?([\\d.]+) (?:Through )([\\d]{2})\\/([\\d]{4}) .*?\\\$ ?([\\d.]+)\\n*",RegexOption.MULTILINE)
    // grp[1] := choice company name
    // grp[2] := supplier base rate
    // grp[3] := supplier total rate
    // grp[4] := thru month
    // grp[5] := thru year
    // grp[6] := etf
    text = thruRegex.replace(text) {thruMatch:MatchResult ->
        val thruDate:LocalDate = LocalDate.of(thruMatch.groupValues[thruRegexCols.THRU_YR.num].toInt(),thruMatch.groupValues[thruRegexCols.THRU_MN.num].toInt(),1).with(TemporalAdjusters.lastDayOfMonth())
        val mnDiff:Int = Period.between(rec.file_date, thruDate).years*12 + Period.between(rec.file_date, thruDate).months
        thruMatch.value.replace(Regex("(?:Through )([\\d]{2})/([\\d]{4})"),"$mnDiff -Month")
    }

    //TODO: can we assume these are always in the same order? what if some are not present?
    val posFixedRate = text.indexOf("Fixed Rate Plan")
    val posMnVarCap = text.indexOf("Monthly Variable Capped Rate Plan")
    val posMnVarRate = text.indexOf("Monthly Variable Rate Plan")
    val posQtVarRate = text.indexOf("Quarterly Variable Rate Plan")
    val posVarRatePlan = "(?<!Monthly )(?<!Quarterly )Variable Rate Plan".toRegex(RegexOption.MULTILINE).find(text)?.range?.start ?: 0

    val ratesSort = linkedMapOf<String, Int>("Fixed Rate" to posFixedRate , "Monthly Variable Capped Rate Plan" to posMnVarCap ,
        "Monthly Variable Rate Plan" to posMnVarRate , "Quarterly Variable Rate Plan" to posQtVarRate ,
        "Variable Rate Plan" to posVarRatePlan).toList().sortedBy { (key, value) -> value }.filter{it.second>-1}

    val ratesRange = mutableMapOf<String, IntRange>()

    for (i in ratesSort.indices) {
        if (i<ratesSort.indices.last) {
            ratesRange[ratesSort[i].first] = ratesSort[i].second until ratesSort[i+1].second
        } else {
            ratesRange[ratesSort[i].first] = ratesSort[i].second until text.length
        }

    }

    val stdRegex = Regex("^ *([^\$\\d\\n]*)[ ]?[\\d]* *\\\$ *([\\d.]+) \\\$ *([\\d.]+) ([\\d]+) *-Month .*? \\\$ *([\\d.]+)\\n*", RegexOption.MULTILINE)
    val matches = stdRegex.findAll(text)
    var last_co:String = ""
    matches.forEach { result ->
        rec.choice_company = result.groupValues[stdRegexCols.COMPANY.num]
        if(rec.choice_company!="") {
            last_co = rec.choice_company
        }
        if(rec.choice_company == "") {
            rec.choice_company = last_co
        }
        rec.len_contract = result.groupValues[stdRegexCols.CT_LENGTH.num]
        rec.contract_type = ratesRange.filter { it.value.contains(result.range.first) }.toList()[0].first
        rec.choice_price_per_mcf = (result.groupValues[stdRegexCols.SUP_TOT_RATE.num].toDouble()*unitsMultiplier).toString()
        rec.choice_etf = result.groupValues[stdRegexCols.ETF.num]
        records.add(rec.copy())
    }

    doc.close()
}

fun extractVecCGOOld(file:File,rec:Record) {
    val doc: PDDocument = PDDocument.load(file)
    val pdfTextStripper = PDFTextStripper()
    pdfTextStripper.sortByPosition = true
    var text = pdfTextStripper.getText(doc)
//    text = text.slice(text.indexOf("Supplier Plans, Rates, Terms and Descriptions")..text.length-1)
//    val spChar = if (rec.file_date.isAfter(LocalDate.of(2009,12,23)) && rec.file_date.isBefore(LocalDate.of(2012,8,3))) "" else " "
    val spChar = ""
    text = text.replace("Monthly(?! Variable)".toRegex(RegexOption.MULTILINE), "1$spChar-Month")
    text = text.replace("Billing Cycle\n","")
    text = text.replace(Regex("""Page [\d] of [\d]\n"""),"")

    //val abbrevs = mapOf<String,String>("Dominion Energy Ohio" to "DEO", "Columbia Gas of Ohio" to "Columbia Gas","Duke Energy Ohio" to "Duke", "Vectren Energy Delivery" to "Vectren")

    //val scoPattern = if (rec.utility == "Dominion Energy Ohio")  "current(?: total)*(?: SCO)* rate is \\\$ *([\\d.]+) per.*?(ccf|mcf|MCF|CCF|Ccf|Mcf)[ \\.)\\s]+Effective(?: from)* ([\\d]{2}\\/[\\d]{2}\\/[\\d]{4}) through ([\\d]{2}\\/[\\d]{2}\\/[\\d]{4})" else "current total(?: SCO| GCR)* rate is \\\$ *([\\d.]+) per.*?(ccf|mcf|MCF|CCF|Ccf|Mcf)[ )\\s]+Effective(?: from)* ([\\S]+)\\s([^,]+),\\s([\\S]+)\\s(?:through|to)\\s([\\S]+)\\s([^,]+),\\s([\\S]+)\\s"
    val scoPattern = "(?:Duke Energy Ohio's \\(Duke\\)|Columbia Gas of Ohio's \\(CGO\\)|Vectren's) current total rate is *\\\$(?<rate>[\\d\\.]+) per (?<units>.*?)\\nEffective from *(?<monthS>[A-z]+) (?<dayS>[\\d]+), (?<yearS>[\\d]+) to (?<monthE>[A-z]+) (?<dayE>[\\d]+), (?<yearE>[\\d]+)"
    val scoRegex:Regex = Regex(scoPattern, RegexOption.IGNORE_CASE)
    val scoMatch = scoRegex.find(text)
    println(rec.utility)
    var unitsMultiplier = 1 // will change to 10 if ccf
    if (scoMatch!=null) {
        val groups = (scoMatch.groups as MatchNamedGroupCollection)
        rec.sco_rate = groups["rate"]!!.value.toDouble()
        if (groups["units"]!!.value.contains("ccf",ignoreCase = true)){
            rec.sco_rate *= 10
            unitsMultiplier = 10
        }
        rec.sco_eff_start = LocalDate.of(groups["yearS"]!!.value.toInt(),Month.valueOf(groups["monthS"]!!.value.toUpperCase()),groups["dayS"]!!.value.toInt())
        rec.sco_eff_end = LocalDate.of(groups["yearE"]!!.value.toInt(),Month.valueOf(groups["monthE"]!!.value.toUpperCase()),groups["dayE"]!!.value.toInt())
    }
    println(rec.sco_rate)
    println(rec.sco_eff_start)
    println(rec.sco_eff_end)

    val thruRegex = Regex("""Through ([\d]{1,2})/([\d]{4})""",RegexOption.MULTILINE)
    // grp[1] := choice company name
    // grp[2] := supplier base rate
    // grp[3] := supplier total rate
    // grp[4] := thru month
    // grp[5] := thru year
    // grp[6] := etf
    text = thruRegex.replace(text) {thruMatch:MatchResult ->
        val thruDate:LocalDate = LocalDate.of(thruMatch.groupValues[2].toInt(),thruMatch.groupValues[1].toInt(),1).with(TemporalAdjusters.lastDayOfMonth())
        val mnDiff:Int = Period.between(rec.file_date, thruDate).years*12 + Period.between(rec.file_date, thruDate).months
        thruMatch.value.replace(Regex("""Through ([\d]{1,2})/([\d]{4})"""),"$mnDiff-Month")
    }

    //TODO: can we assume these are always in the same order? what if some are not present?
    val posFixedRate = text.indexOf("Fixed Rate Plans")
    val posMnVarCap = text.indexOf("Monthly Variable Capped-Rate Plans")
    val posMnVarRate = text.indexOf("Monthly Variable Rate Plans")
    val posQtVarRate = text.indexOf("Quarterly Variable Rate Plans")
    val posVarRatePlan = "(?<!Monthly )(?<!Quarterly )Variable Rate Plan".toRegex(RegexOption.MULTILINE).find(text)?.range?.start ?: 0

    val ratesSort = linkedMapOf<String, Int>("Fixed Rate" to posFixedRate , "Monthly Variable Capped Rate Plan" to posMnVarCap ,
        "Monthly Variable Rate Plan" to posMnVarRate , "Quarterly Variable Rate Plan" to posQtVarRate ,
        "Variable Rate Plan" to posVarRatePlan).toList().sortedBy { (key, value) -> value }.filter{it.second>-1}

    val ratesRange = mutableMapOf<String, IntRange>()

    for (i in ratesSort.indices) {
        if (i<ratesSort.indices.last) {
            ratesRange[ratesSort[i].first] = ratesSort[i].second until ratesSort[i+1].second
        } else {
            ratesRange[ratesSort[i].first] = ratesSort[i].second until text.length
        }

    }

    var lines = text.split('\n')
    val arrSplitLocation = lines.indexOf("Plan ID Supplier Base Rate Supplier Total Rate Contract Term Plan Description Termination Fee")
    lines = lines.slice(arrSplitLocation+1 until lines.size)
    var prevLine = ""
    val stdRegex = Regex("""(?<comp>.*?)(?<planId>[\d]+) +\$(?<baseRate>[\d\.]+) +\$(?<totalRate>[\d\.]+) +(?<length>[\d]+)+.*?\$(?<etf>[\d\.]+)$""", RegexOption.MULTILINE)
    val matches = stdRegex.findAll(text)
    val tempPlans = mutableListOf<Plan>()
    matches.forEach { result ->
        val groups = result.groups as MatchNamedGroupCollection
        val plan = Plan()
        plan.company = groups["comp"]!!.value
        plan.planId = groups["planId"]!!.value
        plan.baseRate = (groups["baseRate"]!!.value.toDouble()*unitsMultiplier).toString()
        plan.totalRate = (groups["totalRate"]!!.value.toDouble()*unitsMultiplier).toString()
        plan.length = groups["length"]!!.value
        plan.etf = groups["etf"]!!.value
        plan.type = ratesSort.filter { it.second<=result.range.first }.sortedByDescending { (key, value) -> value }[0].first
        tempPlans.add(plan)
    }
    var last_company = ""
    tempPlans.forEachIndexed { idx,plan ->
        if (plan.company==" ") {
            plan.company=last_company
        }
        rec.choice_company=plan.company
        rec.len_contract = plan.length
        rec.contract_type = plan.type
        rec.choice_price_per_mcf = plan.totalRate
        rec.choice_etf = plan.etf
        records.add(rec.copy())
        last_company=plan.company
    }

    doc.close()
}

fun extractDukeOld(file:File,rec:Record) {
    val doc: PDDocument = PDDocument.load(file)
    val pdfTextStripper = PDFTextStripper()
    pdfTextStripper.sortByPosition = true
    var text = pdfTextStripper.getText(doc)
//    text = text.slice(text.indexOf("Supplier Plans, Rates, Terms and Descriptions")..text.length-1)
//    val spChar = if (rec.file_date.isAfter(LocalDate.of(2009,12,23)) && rec.file_date.isBefore(LocalDate.of(2012,8,3))) "" else " "
    val spChar = ""
    text = text.replace("Monthly(?! Variable)".toRegex(RegexOption.MULTILINE), "1$spChar-Month")
    text = text.replace("Billing Cycle\n","")
    text = text.replace(Regex("""Page [\d] of [\d]\n"""),"")

    //val abbrevs = mapOf<String,String>("Dominion Energy Ohio" to "DEO", "Columbia Gas of Ohio" to "Columbia Gas","Duke Energy Ohio" to "Duke", "Vectren Energy Delivery" to "Vectren")

    //val scoPattern = if (rec.utility == "Dominion Energy Ohio")  "current(?: total)*(?: SCO)* rate is \\\$ *([\\d.]+) per.*?(ccf|mcf|MCF|CCF|Ccf|Mcf)[ \\.)\\s]+Effective(?: from)* ([\\d]{2}\\/[\\d]{2}\\/[\\d]{4}) through ([\\d]{2}\\/[\\d]{2}\\/[\\d]{4})" else "current total(?: SCO| GCR)* rate is \\\$ *([\\d.]+) per.*?(ccf|mcf|MCF|CCF|Ccf|Mcf)[ )\\s]+Effective(?: from)* ([\\S]+)\\s([^,]+),\\s([\\S]+)\\s(?:through|to)\\s([\\S]+)\\s([^,]+),\\s([\\S]+)\\s"
    val scoPattern = "(?:Duke Energy Ohio's \\(Duke\\)|Columbia Gas of Ohio's \\(CGO\\)|Vectren's) current total rate is *\\\$(?<rate>[\\d\\.]+) per (?<units>.*?)\\nEffective from *(?<monthS>[A-z]+) (?<dayS>[\\d]+), (?<yearS>[\\d]+) to (?<monthE>[A-z]+) (?<dayE>[\\d]+), (?<yearE>[\\d]+)"
    val scoRegex:Regex = Regex(scoPattern, RegexOption.IGNORE_CASE)
    val scoMatch = scoRegex.find(text)
    println(rec.utility)
    var unitsMultiplier = 1 // in case need convert ccf->mcf
    if (scoMatch!=null) {
        val groups = (scoMatch.groups as MatchNamedGroupCollection)
        rec.sco_rate = groups["rate"]!!.value.toDouble()
        if (groups["units"]!!.value.contains("ccf",ignoreCase = true)){
            rec.sco_rate *= 10
            unitsMultiplier = 10
        }
        rec.sco_eff_start = LocalDate.of(groups["yearS"]!!.value.toInt(),Month.valueOf(groups["monthS"]!!.value.toUpperCase()),groups["dayS"]!!.value.toInt())
        rec.sco_eff_end = LocalDate.of(groups["yearE"]!!.value.toInt(),Month.valueOf(groups["monthE"]!!.value.toUpperCase()),groups["dayE"]!!.value.toInt())
    }
    println(rec.sco_rate)
    println(rec.sco_eff_start)
    println(rec.sco_eff_end)

    val thruRegex = Regex("""Through ([\d]{1,2})/([\d]{4})""",RegexOption.MULTILINE)
    // grp[1] := choice company name
    // grp[2] := supplier base rate
    // grp[3] := supplier total rate
    // grp[4] := thru month
    // grp[5] := thru year
    // grp[6] := etf
    text = thruRegex.replace(text) {thruMatch:MatchResult ->
        val thruDate:LocalDate = LocalDate.of(thruMatch.groupValues[2].toInt(),thruMatch.groupValues[1].toInt(),1).with(TemporalAdjusters.lastDayOfMonth())
        val mnDiff:Int = Period.between(rec.file_date, thruDate).years*12 + Period.between(rec.file_date, thruDate).months
        thruMatch.value.replace(Regex("""Through ([\d]{1,2})/([\d]{4})"""),"$mnDiff-Month")
    }

    //TODO: can we assume these are always in the same order? what if some are not present?
    val posFixedRate = text.indexOf("Fixed Rate Plans")
    val posMnVarCap = text.indexOf("Monthly Variable Capped-Rate Plans")
    val posMnVarRate = text.indexOf("Monthly Variable Rate Plans")
    val posQtVarRate = text.indexOf("Quarterly Variable Rate Plans")
    val posVarRatePlan = "(?<!Monthly )(?<!Quarterly )Variable Rate Plan".toRegex(RegexOption.MULTILINE).find(text)?.range?.start ?: 0

    val ratesSort = linkedMapOf<String, Int>("Fixed Rate" to posFixedRate , "Monthly Variable Capped Rate Plan" to posMnVarCap ,
        "Monthly Variable Rate Plan" to posMnVarRate , "Quarterly Variable Rate Plan" to posQtVarRate ,
        "Variable Rate Plan" to posVarRatePlan).toList().sortedBy { (key, value) -> value }.filter{it.second>-1}

    val ratesRange = mutableMapOf<String, IntRange>()

    for (i in ratesSort.indices) {
        if (i<ratesSort.indices.last) {
            ratesRange[ratesSort[i].first] = ratesSort[i].second until ratesSort[i+1].second
        } else {
            ratesRange[ratesSort[i].first] = ratesSort[i].second until text.length
        }

    }

    var lines = text.split('\n')
    val arrSplitLocation = lines.indexOf("Plan ID Supplier Base Rate Supplier Total Rate Contract Term Plan Description Termination Fee")
    lines = lines.slice(arrSplitLocation+1 until lines.size)
    var prevLine = ""
    val stdRegex = Regex("""(?<planId>[\d]+) +\$(?<baseRate>[\d\.]+) +\$(?<totalRate>[\d\.]+) +(?<length>[\d]+)+.*?\$(?<etf>[\d\.]+)""", RegexOption.MULTILINE)
    val relLine = mutableListOf<DukeOldDTO>()
    lines.forEachIndexed { idx, line ->
        if (stdRegex.containsMatchIn(line)) {
            val match = stdRegex.find(line)
            val groups = match!!.groups as MatchNamedGroupCollection
            val obj = mutableMapOf<String, String>()
            obj["planId"] = groups["planId"]!!.value
            obj["baseRate"] = (groups["baseRate"]!!.value.toDouble()*unitsMultiplier).toString()
            obj["totalRate"] = (groups["totalRate"]!!.value.toDouble()*unitsMultiplier).toString()
            obj["length"] = groups["length"]!!.value
            obj["etf"] = groups["etf"]!!.value
            obj["type"] = ratesSort.filter { it.second<=text.indexOf(line) }.sortedByDescending { (key, value) -> value }[0].first
            val dto:DukeOldDTO = DukeOldDTO("plan",idx,obj)
            relLine.add(dto)
            return@forEachIndexed
        }
        if (stdRegex.containsMatchIn(lines[idx-1])) {
            // based on data discovery, all valid Duke Choice providers contain one of these strings and no invalid choices do
            listOf("energy","gas","llc","inc","source","power").forEach { word ->
                if (line.toLowerCase().contains(word)) {
                    val obj = mutableMapOf<String, String>()
                    obj["name"] = line
                    val dto:DukeOldDTO = DukeOldDTO("company",idx,obj)
                    relLine.add(dto)
                    return@forEachIndexed
                }
            }
        }
        // else do nothing
    }
    for ((idx,line) in relLine.withIndex()) {
        if (idx<relLine.size-1) {
            if (relLine[idx+1].type=="company") {
                relLine[idx].value["company"] = relLine[idx + 1].value["name"]!!
                continue
            }
            if (idx>=1) {
                if (relLine[idx-1].type=="company") {
                    relLine[idx].value["company"] = relLine[idx-1].value["name"]!!
                    continue
                }
            }
            if (idx>=2) {
                if (relLine[idx-2].type=="company") {
                    relLine[idx].value["company"] = relLine[idx-2].value["name"]!!
                    continue
                }
            }
        }
        relLine[idx].value["company"] = relLine[idx-1].value["company"]!!
    }
    for (plan in relLine.filter { it.type == "plan" }) {
        rec.choice_company=plan.value["company"]!!
        rec.len_contract = plan.value["length"]!!
        rec.contract_type = plan.value["type"]!!
        rec.choice_price_per_mcf = plan.value["totalRate"]!!
        rec.choice_etf = plan.value["etf"]!!
        records.add(rec.copy())
    }

//    val matches = stdRegex.findAll(text)
//    var last_co:String = ""
//    matches.forEach { result ->
//        val groups = result.groups as MatchNamedGroupCollection
//        rec.choice_company = groups["supplier"]!!.value
//        if(rec.choice_company!="") {
//            last_co = rec.choice_company
//        }
//        if(rec.choice_company == "") {
//            rec.choice_company = last_co
//        }
//        rec.len_contract = groups["length"]!!.value
//        rec.contract_type = ratesRange.filter { it.value.contains(result.range.first) }.toList()[0].first
//        rec.choice_price_per_mcf = groups["totalRate"]!!.value
//        rec.choice_etf = groups["etf"]!!.value
//        records.add(rec.copy())
//    }

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
    } else if ((rec.file_date.isAfter(LocalDate.of(2009,9,23))&&(rec.utility=="Dominion Energy Ohio"))
        || (rec.file_date.isAfter(LocalDate.of(2013,3,12))&&(listOf("Duke Energy Ohio","Vectren Energy Delivery").contains(rec.utility)))
        || (rec.file_date.isAfter(LocalDate.of(2013,3,4)) && (rec.utility=="Columbia Gas of Ohio"))){
        extractMiddle(file, rec)
    } else if ((rec.file_date.isAfter(LocalDate.of(2009,12,8)) && ((listOf("Vectren Energy Delivery","Columbia Gas of Ohio").contains(rec.utility))))) {
        extractVecCGOOld(file,rec)
    } else if (rec.file_date.isAfter(LocalDate.of(2009,12,8)) && rec.utility=="Duke Energy Ohio") {
        extractDukeOld(file,rec)
    }
}

fun main() {
//    val df: DataFrame = dataFrameOf("Utility","File Date","SCO Rate","SCO Effective Start","SCO Effective End","Choice Company","Contract Type","Length of Contract","Price per MCF","Monthly Fee","ETF","File Name")
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
        } catch (ex:Exception) {
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