import os.path
from tqdm import tqdm

for dirpath, _, files in tqdm(os.walk("/Users/wwelch/Downloads/PUCO Apples to Apples")):
    if not {"Columbia Gas","Dominion Energy Ohio","Duke","Vectron"} & set(os.path.normpath(dirpath).split(os.sep)):
        continue
    for file in files:
        if not file.lower().endswith(".pdf"):
            continue
        if file.startswith("0"):
            os.rename(os.path.join(dirpath,file),os.path.join(dirpath,"2"+file))