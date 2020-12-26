import os.path
from tqdm import tqdm

for dirpath, _, files in tqdm(os.walk("/Users/wwelch/Downloads/PUCO Apples to Apples/Data")):
    for file in files:
        dirpath:str
        file:str
        # print(dirpath,file,file[:-4]+'_Residential.pdf','\n')
        if dirpath.find('Residential')>-1:
            os.rename(os.path.join(dirpath, file),os.path.join(dirpath,file[:-4]+'_Residential.pdf'))
        elif dirpath.find('Small Commercial')>-1:
            os.rename(os.path.join(dirpath, file), os.path.join(dirpath, file[:-4] + '_Sm_Com.pdf'))