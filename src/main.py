from config import *
from helper import *


def build_results(ticket_id, date):
    create_source_code_embedding(ticket_id, date)




def main():
    make_folder(rank_results_root_dir)
    make_folder(source_embedding_root_dir)
    make_folder(ticket_embedding_root_dir)
    if is_building:
        for ticket_id in ticket_id_list:
            create_ticket_embedding(ticket_id)
            similar_sha_dict = ticket_id_close_sha_dict[ticket_id]
            date = ticket_id_date_dict[ticket_id]
            revert_repos(similar_sha_dict)
            build_results(ticket_id, date)
    elif is_evaluating:
        evaluate()



if __name__ == '__main__':
    main()