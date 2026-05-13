import request from '@/api/request'

export function listUsers(username, page, size) {
  return request.get('/user/list', { params: { username: username, page: page || 1, size: size || 20 } })
}

export function saveUser(data) {
  return request.post('/user/save', data)
}

export function deleteUser(id) {
  return request.delete('/user/' + id)
}
