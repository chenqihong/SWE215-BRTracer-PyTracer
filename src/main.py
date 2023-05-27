from config import *
from util import *


def create_ticket_embedding(ticket_info_dict):
    for ticket_id in tqdm(ticket_info_dict, desc="creating ticket embeddings: "):
        info = ticket_info_dict[ticket_id]
        embedding = build_source_code_nl_embedding(info)
        save_name = build_embedding_save_name(ticket_id, 'info')
        save_dir = os.path.join(ticket_embedding_collection_dir, save_name)
        torch.save(embedding, save_dir)


def create_source_code_embedding(ticket_repos_dict):
    for ticket_id in ticket_repos_dict:
        target_repo_list = ticket_repos_dict[ticket_id]
        for repo_name in target_repo_list:
            repo_dir = os.path.join(all_repos_root_dir, repo_name)
            source_code_list = extract_source_code_list(repo_dir)
            for source_code_file_dir in tqdm(source_code_list, desc="create embedding for source code"):
                source_code_partial_dir = source_code_file_dir.replace(all_repos_root_dir, "")
                if source_code_partial_dir in black_list_file:
                    continue
                embed_save_name = build_embedding_save_name(source_code_partial_dir, "code")
                save_dir = os.path.join(source_code_embedding_collection_dir, repo_name, embed_save_name)
                if os.path.exists(save_dir):
                    continue
                close_code_segment_embed = find_close_segment_embed(source_code_file_dir, ticket_id, repo_name)
                if close_code_segment_embed.size()[0] == 0:
                    black_list_file.append(source_code_file_dir)
                    continue
                torch.save(close_code_segment_embed, save_dir)


def evaluation(target_tickets_list):
    ticket_id_solution_dict = load_tickets_solution(target_tickets_list)
    performance_dict = defaultdict(float)
    for ticket_id in ticket_id_solution_dict:
        ticket_solution_list = ticket_id_solution_dict[ticket_id]
        ticket_answer_list = load_ticket_answer_list(ticket_id)
        ticket_answer_list = ticket_answer_list[:len(ticket_solution_list)]
        performance_score = find_performance_score(ticket_answer_list, ticket_solution_list)
        performance_dict[ticket_id] = performance_score
    return performance_dict


def rank_source_code_files(ticket_id, source_code_list, repo_name):
    rank_list = list()
    for full_file_dir in tqdm(source_code_list, desc="rank single file: "):
        if full_file_dir in black_list_file:
            continue
        file_partial_dir = full_file_dir.replace(all_repos_root_dir, '')
        if file_partial_dir in black_list_file:
            continue
        info_embed = load_code_info_embed(ticket_id, repo_name, 'info')
        source_embed = load_code_info_embed(file_partial_dir, repo_name, 'code')
        closeness = find_embeds_closeness(info_embed, source_embed)
        rank_list.append((ticket_id, file_partial_dir, closeness))
    return rank_list


if __name__ == '__main__':
    download_repos()
    target_tickets_list = read_from_txt(working_ticket_ids_dir)
    ticket_info_dict = read_all_tickets(target_tickets_list)
    ticket_repos_dict = load_ticket_repos_dict()
    if is_building:
        make_folder(ticket_rank_result_dir)
        make_folder(source_code_embedding_collection_dir)
        make_folder(ticket_embedding_collection_dir)
        create_ticket_embedding(ticket_info_dict)
        create_source_code_embedding(ticket_repos_dict)
        for ticket_id in ticket_repos_dict:
            ticket_rank_list = list()
            ticket_repo_list = ticket_repos_dict[ticket_id]
            for repo_name in tqdm(ticket_repo_list, desc="rank each repo: "):
                repo_full_dir = os.path.join(all_repos_root_dir, repo_name)
                source_code_list = extract_source_code_list(repo_full_dir)
                ticket_rank_list += rank_source_code_files(ticket_id, source_code_list, repo_name)
            ticket_rank_list = sorted(ticket_rank_list, key=lambda x: x[-1])
            with open(ticket_rank_result_dir + '/' + ticket_id + '.txt', 'w+') as f:
                for ticket_rank_info in ticket_rank_list:
                    f.write(str(ticket_rank_info) + '\n')
    if is_evaluate:
        print("Start evaluating")
        performance_dict = evaluation(target_tickets_list)
        for ticket_id in performance_dict:
            print("Ticket ID: " + ticket_id + " score: " + performance_dict[ticket_id])
