# -*- coding: utf-8 -*-
"""
Spyder Editor
@author:wujs
@time: 2017/2/7
"""
import codecs
import pycurl
import StringIO
import urllib
import json

 
response = StringIO.StringIO()
post_data_dic = {"text":"Reformist allies of Yugoslav President Vojislav Kostunica have scored a decisive victory in today 's Serbian parliamentary elections ."}
crl = pycurl.Curl()
crl.fp = StringIO.StringIO()
# Option -d/--data <data>   HTTP POST data
crl.setopt(crl.POSTFIELDS,  urllib.urlencode(post_data_dic))

crl.setopt(pycurl.URL,'http://localhost:8080/aida/service/disambiguate')
#crl.setopt(pycurl.HTTPHEADER, ['Content-Type: application/json'])

crl.setopt(crl.WRITEFUNCTION, crl.fp.write)
crl.perform()
ret =  json.loads(crl.fp.getvalue())
ment_list = ret['mentions']
metadata_dict = ret['entityMetadata']
for item_dict in ment_list:
  offset = item_dict['offset']
  length = item_dict['length']
  entities = metadata_dict[item_dict['bestEntity']['kbIdentifier']]['url']
  
  print offset,length,entities
  
  
  
#fileRet = codecs.open('aceDataSent.txt','w','utf-8')
#with codecs.open('aceData.txt','r','utf-8') as file:
#  for line in file:
#    line = line.strip()
#    word = line.split('\t')[0]
#    if word!=u'':
#      fileRet.write(word+' ')
#    else:
#      fileRet.write(word+'\n')
#
#fileRet.close()    
