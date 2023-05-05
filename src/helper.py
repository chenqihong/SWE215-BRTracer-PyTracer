from config import *


def make_folder(folder_dir: str):
    try:
        os.mkdir(folder_dir)
    except FileExistsError:
        pass


def revert_repos(similar_sha_dict: dict):
    """ TODO: Revert the repos using sha dict """
    pass


def create_ticket_embedding(ticket_id: str):
    
