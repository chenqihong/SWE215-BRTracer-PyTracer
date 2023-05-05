import os
import csv
from collections import defaultdict
from itertools import chain
from transformers import AutoTokenizer, AutoModel
import torch
import torch.nn.functional as F
import numpy as np
import shutil

root_dir = os.path.join(os.getcwd(), "..")
project_repos_root_dir = os.path.join(root_dir, "all_projects_repos")
target_file_type = ".java"
ticket_info_csv_root_dir = os.path.join(root_dir, "ticket_info_csvs")
rank_results_root_dir = os.path.join(root_dir, "rank_results")
tokenizer = AutoTokenizer.from_pretrained("microsoft/codebert-base")
model = AutoModel.from_pretrained("microsoft/codebert-base")
source_embedding_root_dir = os.path.join(root_dir, "all_source_code_embeddings")
ticket_embedding_root_dir = os.path.join(root_dir, "all_ticket_embeddings")
ticket_solution_root_dir = os.path.join(root_dir, "ticket_solutions")

empty_file_dir_list = list()
is_building = True
is_evaluating = True
ticket_id_list = list()
ticket_id_close_sha_dict = {}  # key = ticket id, value = dict, where key = repo, value = sha that is close to this ticket issue date
ticket_id_date_dict = {} # key = ticket id, value = string of date where this ticket is issued