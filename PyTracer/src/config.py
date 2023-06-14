import os
import csv
from collections import defaultdict
from itertools import chain
import glob
from transformers import AutoTokenizer, AutoModel
import torch.nn.functional as F
from tqdm import tqdm
import shutil
import numpy as np
import torch
import pickle

root_dir = os.path.join(os.getcwd(), "..")
all_dataset_dir = os.path.join(root_dir, "all_datasets")
all_repos_root_dir = os.path.join(root_dir, "all_repos")
target_file_type = '.java'

ticket_rank_result_dir = os.path.join(root_dir, "ranked_results")
tokenizer = AutoTokenizer.from_pretrained('microsoft/codebert-base')
model = AutoModel.from_pretrained("microsoft/codebert-base")
source_code_embedding_collection_dir = os.path.join(root_dir, "source_code_embedding_collection")
ticket_embedding_collection_dir = os.path.join(root_dir, "ticket_embedding_collection")
ticket_solution_dir = os.path.join(root_dir, "all_tickets_solution")
download_repos_link_dir = os.path.join(all_dataset_dir, "all_repo_download_link.txt")
working_ticket_ids_dir = os.path.join(all_dataset_dir, "target_ticket_ids.txt")
finished_repo_list = list(os.listdir(source_code_embedding_collection_dir))
ticket_info_dataset_dir = os.path.join(all_dataset_dir, "combined_dataset.csv")
ticket_repo_dir = os.path.join(all_dataset_dir, "ticket_repo.csv")
black_list_file = ['/Users/qihongchen/Desktop/PyTracer/src/../all_repos/Lang/src/java/org/apache/commons/lang/Entities.java']
is_building = True
is_evaluate = True
is_download = False
