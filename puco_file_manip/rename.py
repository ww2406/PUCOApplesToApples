import os.path
from tqdm import tqdm

for dirpath, _, files in tqdm(os.walk("/Users/wwelch/Downloads/PUCO Apples to Apples")):
    if not {"Columbia Gas","Dominion Energy Ohio","Duke","Vectren"} & set(os.path.normpath(dirpath).split(os.sep)):
        continue
    for file in files:
        if not file.lower().endswith(".pdf"):
            continue
        if not file.upper().startswith(("VEDO","COH","CGE","DEO")):
            continue
        if file.upper().startswith("VEDO"):
            os.rename(os.path.join(dirpath,file),os.path.join(dirpath,file[5:-4]+"_Vectren_Energy_Delivery.pdf"))
        elif file.upper().startswith("COH"):
            os.rename(os.path.join(dirpath, file), os.path.join(dirpath, file[5:-4] + "_Columbia_Gas_of_Ohio.pdf"))
        elif file.upper().startswith("CGE"):
            os.rename(os.path.join(dirpath, file), os.path.join(dirpath, file[5:-4] + "_Duke_Energy_Ohio.pdf"))
        elif file.upper().startswith("DEO"):
            os.rename(os.path.join(dirpath, file), os.path.join(dirpath, file[5:-4] + "_Dominion_Energy_Ohio.pdf"))
