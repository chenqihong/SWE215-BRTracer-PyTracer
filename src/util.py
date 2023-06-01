from config import *


def download_repos():
    if is_download:
        os.chdir(all_repos_root_dir)
        with open(download_repos_link_dir, 'r') as f:
            os.system(*[f"git clone {line.strip()}" for line in f])


def read_from_txt(txt_file_dir) -> list:
    with open(txt_file_dir, 'r') as f:
        return [line.strip() for line in f]


def read_all_tickets(target_tickets_list):
    ticket_info_dict = defaultdict(str)
    with open(ticket_info_dataset_dir, 'r') as f:
        reader = csv.reader(f)
        for row in list(reader)[1:]:
            ticket_id, issue_id, resolved, description, summary, created, commit_info_loc = row
            if ticket_id in target_tickets_list:
                ticket_info_dict[ticket_id] = description + '\n' + summary
    return ticket_info_dict


def make_folder(file_dir):
    try:
        os.mkdir(file_dir)
    except FileExistsError:
        pass


def build_source_code_nl_embedding(content: str) -> torch.Tensor:
    """
    Building the embedding of the given source code
    :param content: The content to embed
    :return: The embedding
    """
    code_tokens = tokenizer.tokenize(content)
    cls_token_id, eos_token_id = [0], [2]
    context_embeddings_list = list()
    total_len = len(code_tokens)
    for round_time in range(int(total_len/510) + 1):
        low_index, high_index = 510* round_time, 510*round_time+510
        if high_index >= total_len:
            high_index = total_len
        if high_index == low_index:
            continue
        tokens_ids = tokenizer.convert_tokens_to_ids(code_tokens[low_index:high_index])
        tokens_ids = cls_token_id + tokens_ids + eos_token_id
        if len(tokens_ids) == 512 or round_time == 0:
            context_embeddings_list.append(model(torch.tensor(tokens_ids)[None, :])[0])
    if len(context_embeddings_list) == 0:
        return torch.Tensor([])
    return torch.cat(context_embeddings_list, dim=1)


# def build_source_code_nl_embedding(content, embed_mode="code"):
#     tokens = tokenizer.tokenize(content)
#     if len(tokens) >= 510:
#         tokens = tokens[:510]
#     tokens = [tokenizer.cls_token] + tokens + [tokenizer.eos_token]
#     tokens_ids = tokenizer.convert_tokens_to_ids(tokens)
#     return model(torch.tensor(tokens_ids)[None, :])[0]


def build_embedding_save_name(ticket_id_file_dir, mode):
    if mode == 'code':
        return ticket_id_file_dir.replace('/', '-').split('.')[0].strip() + '.pt'
    else:
        return ticket_id_file_dir + '.pt'


def load_code_info_embed(file_dir_ticket_id, repo_name, mode):
    if mode == 'code':
        file_name = file_dir_ticket_id.replace('/', '-').split('.')[0].strip() + '.pt'
        embed_full_dir = os.path.join(source_code_embedding_collection_dir, repo_name, file_name)
    else:
        file_name = file_dir_ticket_id + '.pt'
        embed_full_dir = os.path.join(ticket_embedding_collection_dir, file_name)
    return torch.load(embed_full_dir)


def load_ticket_repos_dict():
    ticket_repo_dict = defaultdict(list)
    with open(ticket_repo_dir, 'r') as f:
        reader = csv.reader(f)
        for row in list(reader)[1:]:
            ticket_id, repo_list = row
            ticket_repo_dict[ticket_id] = eval(repo_list)
    return ticket_repo_dict


def extract_source_code_list(repo_dir):
    all_files = (chain.from_iterable(glob.glob(os.path.join(x[0], f'*{target_file_type}')) for x in os.walk(repo_dir)))
    all_files = list(all_files)
    all_files = list(map(lambda x: str(x).strip(), all_files))
    return list(set(all_files))


def extract_methods(java_code):
    methods = []
    lines = java_code.split('\n')
    i = 0
    try:
        while i < len(lines):
            line = lines[i].strip()
            if line.startswith('public') or line.startswith('private') or line.startswith('protected'):
                method_signature = line
                method_body = ''
                i += 1
                while i < len(lines) and not lines[i].strip().endswith('}'):
                    method_body += lines[i] + '\n'
                    i += 1
                method_body += lines[i]
                method = method_signature + '\n' + method_body
                methods.append(method)
            i += 1
    except IndexError:
        return [java_code]
    return methods


def find_close_segment_embed(source_code_file_dir, ticket_id, repo_name):
    with open(source_code_file_dir, 'r') as f:
        file_content = f.read()
    print("doing source code file = ", source_code_file_dir)
    method_list = extract_methods(file_content)
    current_closeness = -1
    current_method_imp = ""
    for method_impl_str in method_list:
        method_embed = build_source_code_nl_embedding(method_impl_str)
        ticket_embed = load_code_info_embed(ticket_id, repo_name, "info")
        closeness = find_embeds_closeness(ticket_embed, method_embed)
        if closeness > current_closeness:
            current_closeness = closeness
            current_method_imp = method_impl_str
    return build_source_code_nl_embedding(current_method_imp)


def load_tickets_solution(target_tickets_list):
    ticket_solution_dict = defaultdict(list)
    for ticket_id in target_tickets_list:
        ticket_solution_file_dir = os.path.join(ticket_solution_dir, ticket_id + ".csv")
        ticket_solution_file_dir_list = list()
        with open(ticket_solution_file_dir, 'r') as f:
            reader = csv.reader(f)
            for row in list(reader)[1:]:
                ticket_id, repo_name, commit_id, file_dir, line_change = row
                file_dir_partial = file_dir.replace("/Users/qihchen/Desktop/build_pyTracer_datasets/src/../all_repos/", "")
                ticket_solution_file_dir_list.append(file_dir_partial)
        ticket_solution_dict[ticket_id] = ticket_solution_file_dir_list
    return ticket_solution_dict


def load_ticket_answer_list(ticket_id):
    ans_dir = os.path.join(ticket_rank_result_dir, ticket_id + '.txt')
    answer_list = list()
    with open(ans_dir, 'r') as f:
        for line in f:
            file_dir = eval(line.strip())[1].strip()
            answer_list.append(file_dir[1:])
    return answer_list[::-1]


def find_performance_score(ticket_answer_list, ticket_solution_list):
    total_count = 0
    correct_count = 0
    for answer_file_dir in ticket_answer_list:
        if answer_file_dir in ticket_solution_list:
            correct_count += 1
        total_count += 1
    return correct_count/total_count


def find_embeds_closeness(info_embed, source_embed):
    cos = torch.nn.CosineSimilarity(dim=1)
    info_embed_dim = list(info_embed.size())[1]
    source_embed_dim = list(source_embed.size())[1]
    dimension_diff = abs(info_embed_dim - source_embed_dim)
    if info_embed_dim > source_embed_dim:
        source_embed = F.pad(source_embed, (0, 0, 0, dimension_diff), "constant", 0)
    else:
        info_embed = F.pad(info_embed, (0, 0, 0, dimension_diff), "constant", 0)
    cosine_similarity = cos(source_embed, info_embed)
    return torch.mean(cosine_similarity).item()
