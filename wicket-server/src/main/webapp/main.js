Array.prototype.map = function(transformer) {
	var result = []
	for ( var i = 0; i < this.length; i++) {
		result.push(transformer(this[i]));
	}
	return result;
}

Array.prototype.filter = function(predicate) {
	var result = []
	for ( var i = 0; i < this.length; i++) {
		if (predicate(this[i])) {
			result.push(this[i]);
		}
	}
	return result;
}
Array.prototype.min = function() {
	return this.reduce(function(p, v) {
		return (p < v ? p : v);
	});
}

Array.prototype.max = function() {
	return this.reduce(function(p, v) {
		return (p > v ? p : v);
	});
}