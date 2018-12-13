import os
import operator
def cal_max(str):
  a = str.split('\n')
  count_dict = {}
  for line in a:
      count = count_dict.setdefault(line, 0)
      count += 1
      count_dict[line] = count
  sorted_count_dict = sorted(count_dict.items(), key=operator.itemgetter(1), reverse=True)
  return sorted_count_dict[0][1]

#<bounds minlon="117.70000" minlat="24.40000" maxlon="118.40000" maxlat="24.90000" origin="0.47"/>
root = '../../../ftp-share/数据集/XiamenTrajectory'#读取的批量txt所在的文件夹的路径
root1='/home/mocom_vr/graphhopper_matching/clean_data/'
for (root, dires, files)  in os.walk(root):
    for dirs in dires:
        if not os.path.exists(root1+dirs):
            os.makedirs(root1+dirs)
        file_names = os.listdir(root + '/' + dirs) #读取文件夹下所有的txt的文件名
        for file_name in file_names:  #循环地给文件名加上它前面的路径，以得到它的具体路径
            if file_name == 'table_export.txt':
                continue
            if not os.path.exists(root1+dirs+'/'+file_name+"-out"):
                os.makedirs(root1+dirs+'/'+file_name+"-out")
            if not os.path.exists(root1+dirs+'/'+file_name+"-wrong"):
                os.makedirs(root1+dirs+'/'+file_name+"-wrong")
            fileob = root +'/'+ dirs + '/' + file_name #文件夹路径加上/再加上具体要读的的txt的文件名就定位到了这个txt
            f = open(fileob, mode='r',encoding='GB2312')
            line = f.readline()
            list1 = []
            num = []
            count = 0
            line = f.readline()
            while line:
                a = line.split('\"\t\"')
                jd = a[7]
                wd = a[8]
                t = a[10]
                time = t.split()
                if a[4] in num:
                    if float(wd) <= 24.9 and float(wd) >= 24.4 and float(jd) >= 117.7 and float(jd) <= 118.4:
                        index = num.index(a[4])
                        list1[index] = list1[index]+"<trkpt lat=\""+wd+"\" lon=\""+jd+"\">\n<time>"+"2014-07-01T"+time[2]+"Z</time></trkpt>\n"
                else:
                    if float(wd) <= 24.9 and float(wd) >= 24.4 and float(jd) >= 117.7 and float(jd) <= 118.4:
                        num.append(a[4])
                        list1.append('<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" creator=\"Graphhopper MapMatching 0.11.0\" version=\"1.1\" xmlns:gh=\"https://graphhopper.com/public/schema/gpx/1.1\"><metadata><copyright author=\"OpenStreetMap contributors\"/><link href=\"http://graphhopper.com\"><text>GraphHopper GPX</text></link><time>1970-01-01T00:00:00Z</time></metadata><trk><name>GraphHopper MapMatching</name><trkseg>\n')
                        list1[count] = list1[count]+"<trkpt lat=\""+wd+"\" lon=\""+jd+"\">\n<time>"+"2014-07-01T"+time[2]+"Z</time></trkpt>\n"
                        count = count+1
                line = f.readline()
            f.close()



            count1 = 0
            for string in num:
                list1[count1] = list1[count1]+'</trkseg></trk></gpx>'
                c = list1[count1].split('\n')
                if (len(c)-2)/2-cal_max(list1[count1]) > 4 and (len(c)-2)/2 > 6:
                    fl = open(dirs+'/'+file_name+"-out/"+string+'.gpx', 'w')
                    for i in list1[count1]:
                        fl.write(i)
                    fl.close()
                else:
                    fl = open(dirs+'/'+file_name+"-wrong/" + string + '.gpx', 'w')
                    for i in list1[count1]:
                        fl.write(i)
                    fl.close()
                count1 = count1 + 1