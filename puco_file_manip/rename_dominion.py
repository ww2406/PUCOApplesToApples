import os.path
from tqdm import tqdm

for dirpath, _, files in tqdm(os.walk("/Users/wwelch/Downloads/PUCO Apples to Apples/Dominion Energy Ohio")):
    if not {"Columbia Gas","Dominion Energy Ohio","Duke","Vectron"} & set(os.path.normpath(dirpath).split(os.sep)):
        continue
    file:str
    for file in files:
        if not file.lower().endswith(".pdf"):
            continue
        if file.find("Dominion_East_Ohio")>-1:
            os.rename(os.path.join(dirpath,file),os.path.join(dirpath,file.split('_')[0]+"Dominion_Energy_Ohio.pdf"))
        elif file.find("_Dominion")==-1:
            os.rename(os.path.join(dirpath, file),
                      os.path.join(dirpath, file.replace("Dominion","_Dominion")))
        elif file.find(" _Dominion")>-1:
            os.rename(os.path.join(dirpath, file),
                      os.path.join(dirpath, file.replace(" _Dominion", "_Dominion")))