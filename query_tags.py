import psycopg2
import sys

conn = psycopg2.connect("postgresql://capstone:h29M3JtD7@171.244.205.106:5432/ielts_db")
cur = conn.cursor()

cur.execute("select code, name from question_type")
qt = cur.fetchall()
print("--- Question Types ---")
for r in qt:
    print(r)

cur.execute("select code, name from tag where type = 'QUESTION_TYPE'")
tags = cur.fetchall()
print("--- Tags ---")
for r in tags:
    print(r)

