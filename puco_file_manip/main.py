from io import StringIO

from pdfminer.converter import TextConverter
from pdfminer.layout import LAParams
from pdfminer.pdfdocument import PDFDocument
from pdfminer.pdfinterp import PDFResourceManager, PDFPageInterpreter
from pdfminer.pdfpage import PDFPage
from pdfminer.pdfparser import PDFParser
from pdfminer.high_level import extract_text

import os
import os.path
import pathlib
import pandas as pd
import numpy as np
from datetime import date
import re
import calendar
from tqdm import tqdm

df = pd.DataFrame(columns=["Utility","File Date","SCO Rate","SCO Effective Start","SCO Effective End","Choice Company","Contract Type","Length of Contract","Price per MCF","Monthly Fee","ETF","File Name"])

for dirpath, _, files in os.walk(r"/Users/wwelch/Downloads/PUCO Apples to Apples/Dominion Energy Ohio/test2"):
    for file in tqdm(files):
        if not file.endswith(".pdf"):
            continue
        utility = ""
        if dirpath.find("Columbia Gas")>-1:
            utility = "Columbia Gas"
        elif dirpath.find("Dominion Energy Ohio")>-1:
            utility = "Dominion Ohio"
        elif dirpath.find("Duke")>-1:
            utility = "Duke"
        elif dirpath.find("Vectron")>-1:
            utility = "Vectron"
        else:
            continue
        file_name = file

        _file_base = pathlib.Path(os.path.join(dirpath, file)).stem

        company = " ".join(_file_base.split('_')[1:])
        dt = date.fromisoformat(_file_base.split('_')[:1][0])

        file_text_io = StringIO()
        with open(os.path.join(dirpath,file), 'rb') as in_file:
            parser = PDFParser(in_file)
            doc = PDFDocument(parser)
            rsrcmgr = PDFResourceManager()
            device = TextConverter(rsrcmgr, file_text_io, laparams=LAParams())
            interpreter = PDFPageInterpreter(rsrcmgr, device)
            for page in PDFPage.create_pages(doc):
                interpreter.process_page(page)

        file_text = file_text_io.getvalue()

        # file_text = extract_text(os.path.join(dirpath,file))
        file_text = file_text.replace("\f","")
        file_text = file_text.replace("\n ","\n")

        file_text = re.sub(r"page [^\n]+\nPub[^\n]+\n[^\n]+\n","",file_text)
        while file_text.find("\n\n")>-1:
            file_text = file_text.replace("\n\n","\n")

        no_br_file_text = file_text.replace("\n"," ")

        re_comp = {'Columbia Gas': "CGO's", 'Dominion Ohio': "Dominion's", 'Duke':"Duke's", 'Vectron':"Vectron's"}
        sco_re = re.findall(re_comp[utility] + r"\sSCO[^$]+(\$\d\.[\d]+)[^-]+.{0,2}Effective\s+([\S]+)\s([^,]+),\s([\S]+)\sthrough\s([\S]+)\s([^,]+),\s([\S]+)\s", no_br_file_text)
        if len(sco_re)==0:
            print("error")
        sco_rate = 0.0
        sco_eff_start = date.today()
        sco_eff_end = date.today()
        for sco_matches in sco_re:
            _mn_eff = sco_matches[1]
            _dy_eff = int(sco_matches[2])
            _yr_eff = int(sco_matches[3])
            _mn_last_eff = sco_matches[4]
            _dy_last_eff = int(sco_matches[5])
            _yr_last_eff = int(sco_matches[6])

            _dt_eff = date(_yr_eff, list(calendar.month_name).index(_mn_eff),_dy_eff)
            _dt_last_eff = date(_yr_last_eff, list(calendar.month_name).index(_mn_last_eff), _dy_last_eff)

            if _dt_eff <= dt <= _dt_last_eff:
                sco_rate = float(sco_matches[0][1:])
                sco_eff_start = _dt_eff
                sco_eff_end = _dt_last_eff
                break

        choice_offers = []

        choice_re = re.findall(r"([^\\n]+)\\n\\([\\d]+\\)\\s[\\d]+\\-[\\d]+\\nRate Type: (.+)\\sLength:\\s([\\d]+)\\s[^\\n]+\\n(\\\$[\\d\\.]+) per Mcf Monthly Fee: (\\\$[\\d\\.]+) Early Termination Fee: (\\\$[\\d\\.]+)[^\\n]*\\n", file_text)
        if len(choice_re)==0:
            print("error")
        for choice_mt in choice_re:
            choice_company = choice_mt[0]
            contract_type = choice_mt[1]
            contract_length = choice_mt[2]
            choice_price = choice_mt[3]
            choice_monthly_fee = choice_mt[4]
            choice_etf = choice_mt[5]

            df = df.append(pd.Series([utility,company,dt,sco_rate,sco_eff_start,sco_eff_end,choice_company,contract_type,contract_length,choice_price,choice_monthly_fee,choice_etf,file]))

print('hi')
# print(output_string.getvalue())