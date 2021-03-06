import axios from "axios";


const requests = {
  post: (url, body) => {
    console.log("info =", url, body)
    console.log("Axios = ", axios.defaults.headers, axios.defaults.auth)
    return axios.post(`http://localhost:3000/api${url}`, JSON.stringify(body) , {headers: {"Content-Type":"application/json"}}).then(answer => answer.data)
  },
};

const Auth = {
  register: (username, email, password) =>
    requests.post('/users', { user: { username, email, password } }),
 /* save: user =>
    requests.put('/user', { user })*/
};

/*
const Tags = {
  getAll: () => requests.get('/tags')
};
*/

/*const limit = (count, p) => `limit=${count}&offset=${p ? p * count : 0}`;
const omitSlug = article => Object.assign({}, article, { slug: undefined })
const Articles = {
  all: page =>
    requests.get(`/articles?${limit(10, page)}`),
  byAuthor: (author, page) =>
    requests.get(`/articles?author=${encode(author)}&${limit(5, page)}`),
  byTag: (tag, page) =>
    requests.get(`/articles?tag=${encode(tag)}&${limit(10, page)}`),
  del: slug =>
    requests.del(`/articles/${slug}`),
  favorite: slug =>
    requests.post(`/articles/${slug}/favorite`),
  favoritedBy: (author, page) =>
    requests.get(`/articles?favorited=${encode(author)}&${limit(5, page)}`),
  feed: () =>
    requests.get('/articles/feed?limit=10&offset=0'),
  get: slug =>
    requests.get(`/articles/${slug}`),
  unfavorite: slug =>
    requests.del(`/articles/${slug}/favorite`),
  update: article =>
    requests.put(`/articles/${article.slug}`, { article: omitSlug(article) }),
  create: article =>
    requests.post('/articles', { article })
};

const Comments = {
  create: (slug, comment) =>
    requests.post(`/articles/${slug}/comments`, { comment }),
  delete: (slug, commentId) =>
    requests.del(`/articles/${slug}/comments/${commentId}`),
  forArticle: slug =>
    requests.get(`/articles/${slug}/comments`)
};

const Profile = {
  follow: username =>
    requests.post(`/profiles/${username}/follow`),
  get: username =>
    requests.get(`/profiles/${username}`),
  unfollow: username =>
    requests.del(`/profiles/${username}/follow`)
};*/

export default {
  Auth,
/*  setToken: _token => { token = _token; }*/
};
